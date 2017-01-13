package scala.bench;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;

import java.io.IOException;
import java.nio.file.Paths;

public class Database {

    public static InfluxDB connectDb() {
        Config conf = ConfigFactory.load();
        String influxUrl = conf.getString("influx.url");
        String influxUser = conf.getString("influx.user");
        String influxPassword = conf.getString("influx.password");

        OkHttpClient.Builder client = new OkHttpClient.Builder();

        // workaround https://github.com/influxdata/influxdb-java/issues/268
        client.addNetworkInterceptor(chain -> {
            HttpUrl.Builder fixedUrl = chain.request().url().newBuilder().encodedPath("/influx/" + chain.request().url().encodedPath().replaceFirst("/influxdb", ""));
            return chain.proceed(chain.request().newBuilder().url(fixedUrl.build()).build());
        });

        client.authenticator((route, response) -> {
            String credential = Credentials.basic(influxUser, influxPassword);
            return response.request().newBuilder()
                    .header("Authorization", credential)
                    .build();
        });
        InfluxDB influxDB = InfluxDBFactory.connect(influxUrl, influxUser, influxPassword, client);
        // influxDB.setLogLevel(InfluxDB.LogLevel.FULL);
        return influxDB;
    }
}
