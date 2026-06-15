package io.homeassistant.binding.danfoss.internal;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;

import org.openhab.core.common.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.sonic_amiga.opensdg.java.Connection;
import io.github.sonic_amiga.opensdg.java.GridConnection;

public class GridConnectionKeeper {
    private static final Logger logger = LoggerFactory.getLogger(GridConnectionKeeper.class);
    // One grid connection per private key (per house). Devices of the same house
    // share a single grid connection and multiplex their peer connections over it.
    private static final Map<String, GridConnection> connections = new HashMap<>();
    private static final Map<String, Integer> numUsers = new HashMap<>();

    public synchronized static GridConnection getConnection(String privateKey)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        GridConnection conn = connections.get(privateKey);
        if (conn == null) {
            ScheduledExecutorService scheduler = ThreadPoolManager.getScheduledPool("thingHandler");
            conn = new GridConnection(SDGUtils.ParseKey(privateKey), scheduler);
            connections.put(privateKey, conn);
        }

        if (conn.getState() != Connection.State.CONNECTED) {
            conn.connect(GridConnection.Danfoss);
            logger.info("Successfully connected to Danfoss grid");
        }

        return conn;
    }

    public static synchronized void AddUser(String privateKey) {
        numUsers.merge(privateKey, 1, Integer::sum);
    }

    public static synchronized void RemoveUser(String privateKey) {
        Integer users = numUsers.get(privateKey);
        if (users == null) {
            return;
        }

        if (users > 1) {
            numUsers.put(privateKey, users - 1);
            return;
        }

        numUsers.remove(privateKey);

        GridConnection conn = connections.remove(privateKey);
        if (conn != null) {
            logger.info("Last user is gone, disconnecting from Danfoss grid");
            conn.close();
        }
    }
}
