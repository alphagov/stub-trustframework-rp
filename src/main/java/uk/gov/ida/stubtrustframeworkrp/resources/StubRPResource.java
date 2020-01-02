package uk.gov.ida.stubtrustframeworkrp.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.SignedJWT;
import io.dropwizard.views.View;
import net.minidev.json.JSONObject;
import uk.gov.ida.stubtrustframeworkrp.configuration.StubTrustframeworkRPConfiguration;
import uk.gov.ida.stubtrustframeworkrp.domain.Address;
import uk.gov.ida.stubtrustframeworkrp.rest.Urls;
import uk.gov.ida.stubtrustframeworkrp.service.ResponseService;
import uk.gov.ida.stubtrustframeworkrp.views.IdentityValidatedView;
import uk.gov.ida.stubtrustframeworkrp.views.InvalidResponseView;
import uk.gov.ida.stubtrustframeworkrp.views.TellUsWhoYouAreView;
import uk.gov.ida.stubtrustframeworkrp.views.RPStartView;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.text.ParseException;

@Path("/")
public class StubRPResource {

    private final StubTrustframeworkRPConfiguration configuration;
    private final ResponseService responseService;

    public StubRPResource(StubTrustframeworkRPConfiguration configuration, ResponseService responseService) {
        this.configuration = configuration;
        this.responseService = responseService;
    }

    @GET
    @Path("/")
    public View startPage() {
        return new RPStartView(configuration.getRp());
    }

    @GET
    @Path("/tellUsWhoYouAre")
    public View tellUsWhoYouAre() {
        return new TellUsWhoYouAreView();
    }

    @GET
    @Path("/sendRequest")
    @Produces(MediaType.APPLICATION_JSON)
    public Response sendRequest() {
        URI uri = UriBuilder.fromUri(configuration.getStubBrokerURI()).path(Urls.StubBroker.REQUEST_URI).queryParam("response-uri", configuration.getTrustframeworkRP()).build();
        
        return Response
                .status(302)
                .location(uri)
                .build();
    }

    @POST
    @Path("/response")
    public View receiveResponse(
            @FormParam("jsonResponse") String response, @FormParam("httpStatus") String httpStatus ) {

        if (httpStatus.equals("200") && !(response.length() == 0) && !response.contains("error")) {
            JSONObject jsonResponse;
            JSONObject jsonObject;
            Address address;
            try {
                jsonResponse = JSONObjectUtils.parse(response);
                jsonObject = SignedJWT.parse(jsonResponse.get("jws").toString()).getJWTClaimsSet().toJSONObject();
                address = deserializeAddressFromJWT(jsonObject);
            } catch (ParseException| IOException e) {
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
}
