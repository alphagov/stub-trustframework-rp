package uk.gov.ida.stubtrustframeworkrp.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.ida.stubtrustframeworkrp.configuration.StubTrustframeworkRPConfiguration;

public class RedisService {

    private static final Logger LOG = LoggerFactory.getLogger(RedisService.class);
    private RedisCommands<String, String> commands;
    private StubTrustframeworkRPConfiguration configuration;

    public RedisService(StubTrustframeworkRPConfiguration config) {
        this.configuration = config;
        startup(config);
    }

    public void startup(StubTrustframeworkRPConfiguration config) {
        String vcap = System.getenv("VCAP_SERVICES");
        String redisUri = config.getRedisURI();
        if (vcap != null && vcap.length() > 0) {
            String redisURIFromVcap = getRedisURIFromVcap(vcap);
            if (redisURIFromVcap != null) {
                redisUri = redisURIFromVcap;
            }
        }
        String databaseNumber;
        databaseNumber  = ((configuration.getRp() == "dbs") ? "4" : "5");
        RedisClient client = RedisClient.create(redisUri + "/" + databaseNumber);
        LOG.info("REDIS URI" + redisUri);
        commands = client.connect().sync();
    }

    public void set(String key, String value) {
        commands.set(key, value);
    }

    public String get(String key) {
        return commands.get(key);
    }

    public void delete(String key) {
        commands.del(key);
    }

    public Long incr(String key) {
        return commands.incr(key);
    }

    private String getRedisURIFromVcap(String vcap) {
        JsonElement root = new JsonParser().parse(vcap);
        JsonObject redis = null;
        if (root != null) {
            if (root.getAsJsonObject().has("redis")) {
                redis = root.getAsJsonObject().get("redis").getAsJsonArray().get(0).getAsJsonObject();
            }
            if (redis != null) {
                JsonObject creds = redis.get("credentials").getAsJsonObject();
                String redisURI = creds.get("uri").getAsString();
                LOG.info("This is the Redis URI from VCAP: " + redisURI);
                return redisURI;
            }
        }
        return null;
    }
}