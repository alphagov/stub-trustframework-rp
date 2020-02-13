package uk.gov.ida.stubtrustframeworkrp.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.SignedJWT;
import io.dropwizard.views.View;
import net.minidev.json.JSONObject;
import uk.gov.ida.stubtrustframeworkrp.configuration.StubTrustframeworkRPConfiguration;
import uk.gov.ida.stubtrustframeworkrp.dto.Address;
import uk.gov.ida.stubtrustframeworkrp.dto.OidcResponseBody;
import uk.gov.ida.stubtrustframeworkrp.rest.Urls;
import uk.gov.ida.stubtrustframeworkrp.services.ResponseService;
import uk.gov.ida.stubtrustframeworkrp.views.IdentityValidatedView;
import uk.gov.ida.stubtrustframeworkrp.views.InvalidResponseView;

import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;

@Path("/")
public class StubRpResponseResource {

    private final StubTrustframeworkRPConfiguration configuration;
    private final ResponseService responseService;

    public StubRpResponseResource(StubTrustframeworkRPConfiguration configuration, ResponseService responseService) {
        this.configuration = configuration;
        this.responseService = responseService;
    }

    @POST
    @Path("/authenticationResponse")
    public View handleAuthenticationResponse(String responseBody) throws ParseException, IOException {
        String transactionID = responseService.getTransactionIDFromResponse(responseBody);
        String state = responseService.getStateFromSession(transactionID);
        String nonce = responseService.getNonceFromSession(state);
        OidcResponseBody oidcResponseBody = new OidcResponseBody(responseBody, state, nonce);

        String userCredentials = sendAuthenticationResponseToServiceProvider(oidcResponseBody);
        JSONObject jsonResponse = JSONObjectUtils.parse(userCredentials);
        if (jsonResponse.get("jws") == null) {
            return new InvalidResponseView(jsonResponse.toJSONString());
        }
        JSONObject jsonObject = SignedJWT.parse(jsonResponse.get("jws").toString()).getJWTClaimsSet().toJSONObject();
        Address address = deserializeAddressFromJWT(jsonObject);

        return new IdentityValidatedView(configuration.getRp(), address);
    }

    @POST
    @Path("/response")
    public View receiveResponse(
            @FormParam("jsonResponse") String response, @FormParam("httpStatus") String httpStatus) {

        if (httpStatus.equals("200") && !(response.length() == 0) && !response.contains("error")) {
            JSONObject jsonResponse;
            JSONObject jsonObject;
            Address address;
            try {
                jsonResponse = JSONObjectUtils.parse(response);
                jsonObject = SignedJWT.parse(jsonResponse.get("jws").toString()).getJWTClaimsSet().toJSONObject();
                address = deserializeAddressFromJWT(jsonObject);
            } catch (ParseException | IOException e) {
                return new InvalidResponseView(e.toString());
            }
            return new IdentityValidatedView(configuration.getRp(), address);
        }
        return new InvalidResponseView("Status: " + httpStatus + " with response: " + response);
    }

    private Address deserializeAddressFromJWT(JSONObject jwtJson) throws IOException, ParseException {
        JSONObject credential = JSONObjectUtils.parse(jwtJson.get("vc").toString());
        JSONObject credentialSubject = JSONObjectUtils.parse(credential.get("credentialSubject").toString());
        JSONObject jsonAddress = JSONObjectUtils.parse(credentialSubject.get("address").toString());
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(jsonAddress.toJSONString(), Address.class);
    }


    private String sendAuthenticationResponseToServiceProvider(OidcResponseBody oidcResponseBody) {
        URI uri = UriBuilder.fromUri(configuration.getServiceProviderURI()).path(Urls.ServiceProvider.AUTHN_RESPONSE_URI).build();

        ObjectMapper objectMapper = new ObjectMapper();
        String oidcResponseToString;
        try {
            oidcResponseToString = objectMapper.writeValueAsString(oidcResponseBody);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        HttpRequest request = HttpRequest.newBuilder()
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(oidcResponseToString))
                .uri(uri)
                .build();
        HttpResponse<String> responseBody;
        try {
            responseBody = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return responseBody.body();
    }
}