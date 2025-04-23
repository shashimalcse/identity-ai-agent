package org.wso2.carbon.identity.api.agent.v1.core;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.api.agent.v1.Agent;
import org.wso2.carbon.identity.api.agent.v1.common.AgentConstants;
import org.wso2.carbon.identity.api.agent.v1.error.APIError;
import org.wso2.carbon.identity.api.agent.v1.error.ErrorResponse;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.common.AbstractUserStoreManager;
import org.wso2.carbon.user.core.model.UniqueIDUserClaimSearchEntry;
import org.wso2.carbon.user.core.service.RealmService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import static org.wso2.carbon.identity.api.agent.v1.common.AgentConstants.ErrorMessage.SERVER_ERROR_RETRIEVING_USERSTORE_MANAGER;

/**
 * Service class to interact with the UserStoreManager for agent-related
 * operations.
 */
public class AgentService {

    private final RealmService realmService;

    private static final Log log = LogFactory.getLog(AgentService.class);

    private static final Integer DEFAULT_LIMIT = 20;

    private static final Integer DEFAULT_OFFSET = 0;

    public AgentService(RealmService realmService) {

        this.realmService = realmService;
    }

    /**
     * 
     * Retrieve a list of users from the UserStoreManager.
     *
     * @return List of agents.
     */
    public List<Agent> getAgents(Integer limit, Integer offset) {
        // Check the value of limit and offset, if not available define default values
        if (limit == null || limit <= 0) {
            limit = DEFAULT_LIMIT; // Default limit value
        }
        if (offset == null || offset < 0) {
            offset = DEFAULT_OFFSET; // Default offset value
        }

        List<Agent> agents = new ArrayList<>();

        List<String> claimURIList = new ArrayList<>();
        claimURIList.add("http://wso2.org/claims/displayName");
        claimURIList.add("http://wso2.org/claims/created");
        claimURIList.add("http://wso2.org/claims/metadata.version");

        try {

            AbstractUserStoreManager userStoreManager = (AbstractUserStoreManager) realmService
                    .getTenantUserRealm(IdentityTenantUtil
                            .getTenantId(getTenantDomain()))
                    .getUserStoreManager();

            if (userStoreManager == null) {
                if (log.isDebugEnabled()) {
                    log.debug("Unable to retrieve userstore manager.");
                }
                throw handleError(Response.Status.INTERNAL_SERVER_ERROR, SERVER_ERROR_RETRIEVING_USERSTORE_MANAGER);
            }

            AbstractUserStoreManager agentStoreManager = (AbstractUserStoreManager) userStoreManager
                    .getSecondaryUserStoreManager("AGENT");
            List<org.wso2.carbon.user.core.common.User> users = agentStoreManager.listUsersWithID("*", limit, offset);

            // obtain user claim values
            List<UniqueIDUserClaimSearchEntry> searchEntries;
            
            searchEntries = agentStoreManager.getUsersClaimValuesWithID(users
                    .stream()
                    .map(org.wso2.carbon.user.core.common.User::getUserID)
                    .collect(Collectors.toList()), claimURIList, null);

            // Map<String, Map<String, String>> userClaimsMap = new HashMap<>();

            for (org.wso2.carbon.user.core.common.User user : users) {

                Map<String, String> userClaimValues = new HashMap<>();
                for (UniqueIDUserClaimSearchEntry entry : searchEntries) {
                    if (entry.getUser() != null && StringUtils.isNotBlank(entry.getUser().getUserID())
                            && entry.getUser().getUserID().equals(user.getUserID())) {
                        userClaimValues = entry.getClaims();
                    }
                }
                Agent a = new Agent();
                a.setId(user.getUserID());
                a.setName(user.getUsername());
                a.setName(userClaimValues.get("http://wso2.org/claims/displayName"));
                a.setCreatedAt(userClaimValues.get("http://wso2.org/claims/created"));
                a.setVersion(userClaimValues.get("http://wso2.org/claims/metadata.version"));
                agents.add(a);
            }



            // for (Agent agent : agents) {
            //     Map<String, String> claims = userClaimsMap.get(agent.getId());
            //     if (claims != null) {
            //         agent.setName(claims.get("http://wso2.org/claims/displayName"));
            //         agent.setCreatedAt(claims.get("http://wso2.org/claims/created"));
            //         agent.setVersion(claims.get("http://wso2.org/claims/metadata.version"));
            //     }
            // }

        } catch (UserStoreException e) {
            throw handleException(e, SERVER_ERROR_RETRIEVING_USERSTORE_MANAGER);
        }
        return agents;
    }

    private String getTenantDomain() {

        return IdentityTenantUtil.resolveTenantDomain();
    }

    /**
     * Handle Exceptions.
     *
     * @param e         Exception
     * @param errorEnum Error message enum.
     * @return An APIError.
     */
    private APIError handleException(Exception e, AgentConstants.ErrorMessage errorEnum, String... data) {

        ErrorResponse errorResponse;
        if (data != null) {
            errorResponse = getErrorBuilder(errorEnum).build(log, e, String.format(errorEnum.getDescription(),
                    (Object[]) data));
        } else {
            errorResponse = getErrorBuilder(errorEnum).build(log, e, errorEnum.getDescription());
        }

        if (e instanceof UserStoreException) {
            return new APIError(Response.Status.INTERNAL_SERVER_ERROR, errorResponse);
        } else {
            return new APIError(Response.Status.BAD_REQUEST, errorResponse);
        }
    }

    /**
     * Handle User errors.
     *
     * @param status Http status.
     * @param error  Error .
     * @return An APIError.
     */
    private APIError handleError(Response.Status status, AgentConstants.ErrorMessage error) {

        return new APIError(status, getErrorBuilder(error).build());
    }

    /**
     * Get ErrorResponse Builder for Error enum.
     *
     * @param errorEnum Error message enum.
     * @return Error response for the given errorEnum.
     */
    private ErrorResponse.Builder getErrorBuilder(AgentConstants.ErrorMessage errorEnum) {

        return new ErrorResponse.Builder().withCode(errorEnum.getCode()).withMessage(errorEnum.getMessage())
                .withDescription(errorEnum.getDescription());
    }

}
