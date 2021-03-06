package uk.gov.ida.stubtrustframeworkrp;

import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.views.ViewBundle;
import uk.gov.ida.stubtrustframeworkrp.configuration.StubTrustframeworkRPConfiguration;
import uk.gov.ida.stubtrustframeworkrp.resources.StubRpRequestResource;
import uk.gov.ida.stubtrustframeworkrp.resources.StubRpResponseResource;
import uk.gov.ida.stubtrustframeworkrp.services.RedisService;
import uk.gov.ida.stubtrustframeworkrp.services.RequestService;
import uk.gov.ida.stubtrustframeworkrp.services.ResponseService;

public class StubTrustframeworkRPApplication extends Application<StubTrustframeworkRPConfiguration> {

    public static void main(String[] args) throws Exception {
        new StubTrustframeworkRPApplication().run(args);
    }

    @Override
    public void run(StubTrustframeworkRPConfiguration configuration, Environment environment) {
        RedisService redisService = new RedisService(configuration);
        environment.jersey().register(new StubRpRequestResource(configuration, new RequestService(redisService)));
        environment.jersey().register(new StubRpResponseResource(configuration, new ResponseService(configuration, redisService)));
    }

    @Override
    public void initialize(final Bootstrap<StubTrustframeworkRPConfiguration> bootstrap) {
        bootstrap.addBundle(new ViewBundle<>());
        bootstrap.addBundle(new AssetsBundle("/stylesheets", "/stylesheets", null, "css"));
        bootstrap.addBundle(new AssetsBundle("/javascript", "/javascript", null, "js"));
        bootstrap.addBundle(new AssetsBundle("/assets/fonts", "/assets/fonts", null, "fonts"));
        bootstrap.addBundle(new AssetsBundle("/assets/images", "/assets/images", null, "images"));
        bootstrap.setConfigurationSourceProvider(
                new SubstitutingSourceProvider(
                        bootstrap.getConfigurationSourceProvider(),
                        new EnvironmentVariableSubstitutor(false)));
    }
}
