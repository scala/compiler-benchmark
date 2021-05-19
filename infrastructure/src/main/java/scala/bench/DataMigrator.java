package scala.bench;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;

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
            String oldMeasure = "result_backup_20210519";
            String newMeasure = "result";

            QueryResult queryResult = influxDB.query(new Query("select * from " + oldMeasure + " group by *", "scala_benchmark"));
            for (QueryResult.Result result : queryResult.getResults()) {
                for (QueryResult.Series series : result.getSeries()) {
                    List<String> newFieldNames = new ArrayList<>(series.getColumns());
                    int javaVersionIndex = newFieldNames.indexOf(JAVA_VERSION_TAG_NAME);
                    newFieldNames.remove(javaVersionIndex);
                    assert (newFieldNames.get(0).equals("time"));
                    newFieldNames.remove(0);
                    Point.Builder builder = Point.measurement(newMeasure);
                    Map<String, String> newTags = new HashMap<>(series.getTags());
                    List<Object> newValues = new ArrayList<>(series.getValues().get(0));
                    Object removed = newValues.remove(javaVersionIndex);
                    String time = (String) newValues.remove(0);
                    newTags.put(JAVA_VERSION_TAG_NAME, (String) removed);
                    newTags.entrySet().removeIf(x -> x.getValue() == null || x.getValue().equals(""));
                    builder.tag(newTags);
                    LinkedHashMap<String, Object> newFieldsMap = new LinkedHashMap<>();
                    assert (newFieldNames.size() == newValues.size());
                    for (int i = 0; i < newFieldNames.size(); i++) {
                        String fieldName = newFieldNames.get(i);
                        boolean isLong = fieldName.equals("sampleCount");
                        if (isLong) {
                            newFieldsMap.put(fieldName, ((Number) newValues.get(i)).longValue());
                        } else {
                            newFieldsMap.put(fieldName, newValues.get(i));
                        }
                    }
                    builder.fields(newFieldsMap);
                    Instant parse = Instant.parse(time);
                    builder.time(parse.toEpochMilli(), TimeUnit.MILLISECONDS);
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

