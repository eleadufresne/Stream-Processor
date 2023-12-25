package com.oranges.streamprocessing.util;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** A simple database sink function for the {@link com.oranges.streamprocessing.FruitStreaming} app.
 * @author Éléa Dufresne */
public class FruitDatabaseSink implements SinkFunction<Tuple2<String, Integer>> {

    /* credentials to connect to the database */
    private final String connection_url, username, password;
    public FruitDatabaseSink(String connection_url, String username, String password) {
            this.connection_url = connection_url;
            this.username = username;
            this.password = password;
    }

    /** Updates the number of oranges that share a common feature in this MySQL database
     * @param value Tuple2 containing a feature and the # of oranges that was just observed */
    @Override
    public void invoke(Tuple2<String, Integer> value, Context context) {
        // if there is nothing to insert we might run into issues, so we continue executing
        try (Connection connection = DriverManager.getConnection(connection_url, username, password)) {

            String sql_query = "INSERT INTO pears (feature, count) VALUES (?, ?) ON DUPLICATE " +
                "KEY UPDATE count = count + ?";

            try (PreparedStatement statement = connection.prepareStatement(sql_query)) {
                statement.setString(1, value.f0); // feature
                statement.setInt(2, value.f1); // count
                statement.setInt(3, value.f1); // update count
                statement.executeUpdate();
            } catch (SQLException e) {
                System.err.println("ERROR: could not execute the query: " + e.getMessage());
            }
        } catch (SQLException e) {
            System.err.println("ERROR: could not connect to the database: " + e.getMessage());
        }
    }
}
