import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axway.apim.actions.rest.GETRequest;
import com.axway.apim.actions.rest.RestAPICall;
import com.axway.apim.lib.AppException;
import com.axway.apim.lib.CommandParameters;
import com.axway.apim.lib.ErrorCode;
import com.axway.apim.swagger.APIManagerAdapter;
import com.axway.apim.swagger.api.properties.APIDefintion;
import com.axway.apim.swagger.api.properties.apiAccess.APIAccess;
import com.axway.apim.swagger.api.properties.applications.ClientApplication;
import com.axway.apim.swagger.api.properties.organization.Organization;
import com.axway.apim.swagger.api.properties.quota.APIQuota;
import com.axway.apim.swagger.api.properties.quota.QuotaRestriction;
import com.axway.apim.swagger.api.state.ActualAPI;
import com.axway.apim.swagger.api.state.IAPI;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The APIContract reflects the actual existing API in the API-Manager.
 *
 * @author cwiechmann@axway.com
 */
public class APIManager {

    private static Logger LOG = LoggerFactory.getLogger(APIManager.class);

    private static String apiManagerVersion = null;
    private static APIManagerAdapter apiManagerAdapter;
    public static APIQuota sytemQuotaConfig = null;
    public static APIQuota applicationQuotaConfig = null;
    public final static String SYSTEM_API_QUOTA = "00000000-0000-0000-0000-000000000000";
    public final static String APPLICATION_DEFAULT_QUOTA = "00000000-0000-0000-0000-000000000001";

    public APIManager() {
        if (apiManagerAdapter == null) {
            try {
                LOG.info("Getting apiManagerAdapter singleton instance");
                apiManagerAdapter = APIManagerAdapter.getInstance();
            } catch (AppException e) {
                LOG.error(e.getMessage(), e.getCause());
            }
        }
    }

    public IAPI getApi(final String apiPath) throws AppException, IOException {
        final JsonNode existingAPI =
                apiManagerAdapter.getExistingAPI(apiPath, null, APIManagerAdapter.TYPE_FRONT_END);
        return getAPIManagerAPI(existingAPI);

    }

    /**
     * Creates the API-Manager API-Representation. Basically the "Actual" state of the API.
     *
     * @param jsonConfiguration
     *         the JSON-Configuration which is returned from the API-Manager REST-API (Proxy-Endpoint)
     *
     * @return an APIManagerAPI apiManagerAdapter, which is flagged either as valid, if the API was found or invalid, if
     * not found!
     * @throws AppException
     *         when the API-Manager API-State can't be created
     */
    public IAPI getAPIManagerAPI(JsonNode jsonConfiguration) throws AppException, IOException {
        if (jsonConfiguration == null) {
            IAPI apiManagerAPI = new ActualAPI();
            apiManagerAPI.setValid(false);
            return apiManagerAPI;
        }

        ObjectMapper mapper = new ObjectMapper();
        IAPI apiManagerApi;
        try {
            apiManagerApi = mapper.readValue(jsonConfiguration.toString(), ActualAPI.class);
            return getAPIManagerAPI(apiManagerApi);
        } catch (IOException e) {
            throw e;
        }

    }



    public IAPI getAPIManagerAPI(IAPI apiManagerApi) throws AppException {
        try {
            apiManagerApi
                    .setAPIDefinition(new APIDefintion(getOriginalAPIDefinitionFromAPIM(apiManagerApi.getApiId())));
            if (apiManagerApi.getImage() != null) {
                apiManagerApi.getImage().setImageContent(getAPIImageFromAPIM(apiManagerApi.getId()));
            }
            apiManagerApi.setValid(true);

            addQuotaConfiguration(apiManagerApi);
            addClientOrganizations(apiManagerApi);
            addClientApplications(apiManagerApi);
            addExistingClientAppQuotas(apiManagerApi.getApplications());
            return apiManagerApi;
        } catch (Exception e) {
            throw new AppException("Can't initialize API-Manager API-State.", ErrorCode.API_MANAGER_COMMUNICATION, e);
        }
    }

    private void addClientOrganizations(IAPI apiManagerApi) throws AppException {
        List<String> grantedOrgs = new ArrayList<String>();
        List<Organization> allOrgs = apiManagerAdapter.getAllOrgs();
        for (Organization org : allOrgs) {
            List<APIAccess> orgAPIAccess = apiManagerAdapter.getAPIAccess(org.getId(), "organizations");
            for (APIAccess access : orgAPIAccess) {
                if (access.getApiId().equals(apiManagerApi.getId())) {
                    grantedOrgs.add(org.getName());
                }
            }
        }
        apiManagerApi.setClientOrganizations(grantedOrgs);
    }

    private void addClientApplications(IAPI apiManagerApi) throws AppException {
        if (!apiManagerAdapter.hasAdminAccount()) {
            return;
        }
        List<ClientApplication> existingClientApps = new ArrayList<ClientApplication>();
        List<ClientApplication> allApps = apiManagerAdapter.getAllApps();
        if (APIManagerAdapter.hasAPIManagerVersion("7.7")) {
            existingClientApps = getSubscribedApps(apiManagerApi.getId());
        } else {
            for (ClientApplication app : allApps) {
                List<APIAccess> APIAccess = apiManagerAdapter.getAPIAccess(app.getId(), "applications");
                for (com.axway.apim.swagger.api.properties.apiAccess.APIAccess access : APIAccess) {
                    if (access.getApiId().equals(apiManagerApi.getId())) {
                        existingClientApps.add(app);
                    }
                }
            }
        }
        apiManagerApi.setApplications(existingClientApps);
    }

    private void addExistingClientAppQuotas(List<ClientApplication> existingClientApps) throws AppException {
        if (existingClientApps == null || existingClientApps.size() == 0) {
            return; // No apps subscribed to this APIs
        }
        for (ClientApplication app : existingClientApps) {
            APIQuota appQuota = getQuotaFromAPIManager(app.getId());
            app.setAppQuota(appQuota);
        }
    }

    private static void addQuotaConfiguration(IAPI api) throws AppException {
        ActualAPI managerAPI = (ActualAPI) api;
        try {
            applicationQuotaConfig =
                    getQuotaFromAPIManager(APPLICATION_DEFAULT_QUOTA); // Get the Application-Default-Quota
            sytemQuotaConfig = getQuotaFromAPIManager(SYSTEM_API_QUOTA); // Get the System-Default-Quota
            managerAPI.setApplicationQuota(getAPIQuota(applicationQuotaConfig, managerAPI.getId()));
            managerAPI.setSystemQuota(getAPIQuota(sytemQuotaConfig, managerAPI.getId()));
        } catch (AppException e) {
            LOG.error("Application-Default quota response: '" + applicationQuotaConfig + "'");
            LOG.error("System-Default quota response: '" + sytemQuotaConfig + "'");
            throw e;
        }
    }

    private static APIQuota getQuotaFromAPIManager(String identifier) throws AppException {
        ObjectMapper mapper = new ObjectMapper();
        URI uri;

        try {
            if (identifier.equals(APPLICATION_DEFAULT_QUOTA) || identifier.equals(SYSTEM_API_QUOTA)) {
                uri = new URIBuilder(CommandParameters.getInstance().getAPIManagerURL())
                        .setPath(RestAPICall.API_VERSION + "/quotas/" + identifier).build();
            } else {
                uri = new URIBuilder(CommandParameters.getInstance().getAPIManagerURL())
                        .setPath(RestAPICall.API_VERSION + "/applications/" + identifier + "/quota/").build();
            }
            RestAPICall getRequest = new GETRequest(uri, null, true);
            HttpResponse response = getRequest.execute();
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 403) {
                throw new AppException(
                        "Can't get API-Manager Quota-Configuration, User should have API administrator role",
                        ErrorCode.API_MANAGER_COMMUNICATION);
            }
            if (statusCode != 200) {
                throw new AppException("Can't get API-Manager Quota-Configuration.",
                        ErrorCode.API_MANAGER_COMMUNICATION);
            }
            String config = IOUtils.toString(response.getEntity().getContent(), "UTF-8");
            APIQuota quotaConfig = mapper.readValue(config, APIQuota.class);
            return quotaConfig;
        } catch (URISyntaxException | UnsupportedOperationException | IOException e) {
            throw new AppException("Can't get API-Manager Quota-Configuration.", ErrorCode.API_MANAGER_COMMUNICATION,
                    e);
        }

    }

    private static APIQuota getAPIQuota(APIQuota quotaConfig, String apiId) throws AppException {
        APIQuota apiQuota;
        try {
            for (QuotaRestriction restriction : quotaConfig.getRestrictions()) {
                if (restriction.getApi().equals(apiId)) {
                    apiQuota = new APIQuota();
                    apiQuota.setDescription(quotaConfig.getDescription());
                    apiQuota.setName(quotaConfig.getName());
                    apiQuota.setRestrictions(new ArrayList<QuotaRestriction>());
                    apiQuota.getRestrictions().add(restriction);
                    return apiQuota;
                }
            }
        } catch (Exception e) {
            throw new AppException("Can't parse quota from API-Manager", ErrorCode.API_MANAGER_COMMUNICATION, e);
        }
        return null;
    }

    private static List<Integer> getMajorVersions(String version) {
        List<Integer> majorNumbers = new ArrayList<Integer>();
        String versionWithoutSP = version;
        if (version.contains(" SP")) {
            versionWithoutSP = version.substring(0, version.indexOf(" SP"));
        }
        try {
            String[] versions = versionWithoutSP.split("\\.");
            for (int i = 0; i < versions.length; i++) {
                majorNumbers.add(Integer.parseInt(versions[i]));
            }
        } catch (Exception e) {
            LOG.trace("Can't parse major version numbers in: '" + version + "'");
        }
        return majorNumbers;
    }

    private static int getServicePackVersion(String version) {
        int spNumber = 0;
        if (version.contains(" SP")) {
            try {
                String spVersion = version.substring(version.indexOf(" SP") + 3);
                spNumber = Integer.parseInt(spVersion);
            } catch (Exception e) {
                LOG.trace("Can't parse service pack version in version: '" + version + "'");
            }
        }
        return spNumber;
    }

    private static byte[] getOriginalAPIDefinitionFromAPIM(String backendApiID) throws AppException {
        URI uri;
        try {
            uri = new URIBuilder(CommandParameters.getInstance().getAPIManagerURL())
                    .setPath(RestAPICall.API_VERSION + "/apirepo/" + backendApiID + "/download")
                    .setParameter("original", "true").build();
            RestAPICall getRequest = new GETRequest(uri, null);
            HttpResponse response = getRequest.execute();
            String res = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            return res.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AppException("Can't read Swagger-File.", ErrorCode.CANT_READ_API_DEFINITION_FILE, e);
        }
    }

    private static byte[] getAPIImageFromAPIM(String backendApiID) throws AppException {
        URI uri;
        try {
            uri = new URIBuilder(CommandParameters.getInstance().getAPIManagerURL())
                    .setPath(RestAPICall.API_VERSION + "/proxies/" + backendApiID + "/image").build();
            RestAPICall getRequest = new GETRequest(uri, null);
            HttpEntity response = getRequest.execute().getEntity();
            if (response == null) {
                return null; // no Image found in API-Manager
            }
            InputStream is = response.getContent();
            return IOUtils.toByteArray(is);
        } catch (Exception e) {
            throw new AppException("Can't read Image from API-Manager.", ErrorCode.API_MANAGER_COMMUNICATION, e);
        }
    }

    private static List<ClientApplication> getSubscribedApps(String apiId) throws AppException {
        ObjectMapper mapper = new ObjectMapper();
        String response = null;
        URI uri;
        if (!APIManagerAdapter.hasAPIManagerVersion("7.7")) {
            throw new AppException(
                    "API-Manager: " + apiManagerVersion + " doesn't support /proxies/<apiId>/applications",
                    ErrorCode.UNXPECTED_ERROR);
        }
        try {
            uri = new URIBuilder(CommandParameters.getInstance().getAPIManagerURL())
                    .setPath(RestAPICall.API_VERSION + "/proxies/" + apiId + "/applications").build();
            RestAPICall getRequest = new GETRequest(uri, null, true);
            HttpResponse httpResponse = getRequest.execute();
            response = EntityUtils.toString(httpResponse.getEntity());
            List<ClientApplication> subscribedApps =
                    mapper.readValue(response, new TypeReference<List<ClientApplication>>() {
                    });
            return subscribedApps;
        } catch (Exception e) {
            LOG.error("Error cant load subscribes applications from API-Manager. Can't parse response: " + response);
            throw new AppException("Error cant load subscribes applications from API-Manager.",
                    ErrorCode.API_MANAGER_COMMUNICATION, e);
        }
    }

}
