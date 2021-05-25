package scala.bench;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.TimeUtil;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static scala.bench.Database.connectDb;

public class DataMigrator {

    public static final String JAVA_VERSION_TAG_NAME = "javaVersion";

    public static void main(String[] args) {

        InfluxDB influxDB = connectDb();
        try {
            BatchPoints batchPoints = BatchPoints
                    .database("scala_benchmark")
                    .retentionPolicy("autogen")
                    .consistency(InfluxDB.ConsistencyLevel.ALL)
                    .build();
            influxDB.enableBatch(100, 10, TimeUnit.MILLISECONDS);
            // > SELECT * INTO result_backup_20210519  FROM result GROUP BY *
            //name: result
            //time written
            //---- -------
            //0    14076
            String oldMeasure = "result_backup_20210525";
            String newMeasure = "result";
            QueryResult queryResult = influxDB.query(new Query("select * from " + oldMeasure + " group by *", "scala_benchmark"));
            for (QueryResult.Result result : queryResult.getResults()) {
                for (QueryResult.Series series : result.getSeries()) {
                    Point.Builder builder = Point.measurement(newMeasure);
                    Map<String, String> newTags = new HashMap<>(series.getTags());
                    String javaVersion = newTags.get(JAVA_VERSION_TAG_NAME);
                    if (javaVersion.equals("1.8.0_131-b11")) {
                        newTags.put(JAVA_VERSION_TAG_NAME, "1.8.0_131");
                    }

                    assert (series.getValues().size() == 1);
                    List<Object> newValues = new ArrayList<>(series.getValues().get(0));
                    builder.tag(newTags);

                    List<String> newFieldNames = new ArrayList<>(series.getColumns());
                    LinkedHashMap<String, Object> newFieldsMap = new LinkedHashMap<>();
                    assert (newFieldNames.size() == newValues.size());
                    for (int i = 0; i < newFieldNames.size(); i++) {
                        String fieldName = newFieldNames.get(i);
                        newFieldsMap.put(fieldName, newValues.get(i));
                    }
                    builder.fields(newFieldsMap);
                    long epochMillis = (long) newValues.remove(0) / 1000L / 1000L;
                    builder.time(epochMillis, TimeUnit.MILLISECONDS);
                    Point point = builder.build();
                    batchPoints.point(point);
                }
            }

            influxDB.write(batchPoints);
        } finally {
            influxDB.close();
        }
    }
}

