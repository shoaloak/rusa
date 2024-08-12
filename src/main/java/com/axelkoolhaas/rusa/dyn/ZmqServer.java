package com.axelkoolhaas.rusa.dyn;

import com.axelkoolhaas.rusa.model.json.JsonDTO;
import com.google.gson.Gson;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ZmqServer implements Runnable {
    // Constants
    private static final String START_TAG = "RUSA";
    private static final String STOP_TAG = "HALT";
    private static final String EMPTY_OBJ = "{}";

    // Fields
    private static final Logger logger = LogManager.getLogger(ZmqServer.class);
    private static ZmqServer instance;
    private final ZContext context = new ZContext();
    @Getter
    private boolean ready = false;
    private final List<JsonDTO> feedback = new ArrayList<>();

    // Getters and setters
    public synchronized List<JsonDTO> getFeedback() {
        return Collections.unmodifiableList(this.feedback);
    }

    /**
     * Only store feedback if there is an active connection.
     * @param jsonDTO
     */
    public synchronized void addFeedback(JsonDTO jsonDTO) {
        if (this.ready) {
            this.feedback.add(jsonDTO);
        }
    }

    public synchronized void clearFeedback() {
        this.feedback.clear();
    }

    // Singleton
    public static ZmqServer getInstance() {
        if (instance == null) {
            instance = new ZmqServer();
        }
        return instance;
    }

    // Constructor
    private ZmqServer() {
    }

    @Override
    public void run() {
        // Add shutdown hook for graceful cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                ZmqServer.getInstance().stop()));

        // Main communication loop
//        ZMQ.Socket monitorSocket = this.context.createSocket(SocketType.PAIR);
        ZMQ.Socket socket = null;
        while (!Thread.currentThread().isInterrupted()) {

            try {
                socket = createAndBindSocket();

                if (!handshake(socket)) {
                    continue;
                }

                fuzzCommunicationLoop(socket);

            } catch (NumberFormatException e) {
                // handle the exception here
                logger.error("Request # couldn't be parsed.");
                stop();
            } catch (ZMQException e) {
                logger.error("ZMQ exception in instrumentation communication (disregard if SIGINT).");
                System.exit(1);
            }

            // restart with a clean slate
            if (socket != null) {
                logger.info("Closing socket.");
                socket.setLinger(0);
                socket.close();

                // wait until socket is actually closed
//                ZMQ.Poller poller = this.context.createPoller(1);
//                poll
            }
        }
    }

    public void stop() {
        logger.info("Stopping ZMQ server.");
        this.context.destroy();
    }

    /*
     * Creates a socket and binds it to port 1337.
     * I know this busy wait loop is ugly as hell, but it's the only way I could get it to work.
     * ZMQ.Socket.close() is ASYNC :')
     */
    private ZMQ.Socket createAndBindSocket() {
        ZMQ.Socket socket;
        while (true) {
            try {
                socket = this.context.createSocket(SocketType.REP);
                System.out.println("Starting ZMQ server.");
                if (!socket.bind("tcp://*:1337")) {
                    logger.error("Failed binding to port.");
                    System.exit(1);
                }
                return socket;
            } catch (ZMQException e) {
                int errorCode = e.getErrorCode();
                if (errorCode != 48) {
                    logger.error("ZMQ exception during rebind");
                    System.exit(1);
                }
                logger.info("failure");
            }
        }
    }

    private boolean handshake(ZMQ.Socket socket) {
        String msg;
        logger.info("Waiting for Rusa client...");
        msg = new String(socket.recv(0), ZMQ.CHARSET);
        if (msg.equals(START_TAG)) {
            logger.info("Client connected.");
            this.ready = true;
            socket.send(START_TAG.getBytes(ZMQ.CHARSET), 0);
        } else {
            logger.error("Unexpected tag received.");
            return false;
        }
        return true;
    }

    private void fuzzCommunicationLoop(ZMQ.Socket socket) throws NumberFormatException, ZMQException {
        Gson gson = new Gson();
        String msg;
        long requestIdentifier;

        while (this.ready) {
            msg = new String(socket.recv(0), ZMQ.CHARSET);
//            System.out.println(msg);
            if (msg.equals(STOP_TAG)) {
                logger.info("Client disconnected.");
                this.ready = false;
                break;
            }

            // Handle request identifier
            requestIdentifier = Long.parseLong(msg);
            socket.send(EMPTY_OBJ.getBytes(ZMQ.CHARSET), 0);

            // fuzzing occurs, wait for SYNC
            msg = new String(socket.recv(0), ZMQ.CHARSET);
            if (msg.equals(STOP_TAG)) {
                logger.info("Client disconnected.");
                this.ready = false;
                break;
            }

            // Handle request identifier
            if (requestIdentifier != Long.parseLong(msg)) {
                logger.error("Unexpected request # received.");
                stop();
            }
            // clear request identifier
//                    requestIdentifier = null;

            // Send response
//            getFeedback().forEach(System.out::println);
            socket.send(gson.toJson(getFeedback()).getBytes(ZMQ.CHARSET), 0);
            clearFeedback();
        }
    }

//    public void publish(String data) {
//        if (!this.ready) {
//            System.err.println("Error: no client connected.");
//            return;
//        }
//        ZMQ.Socket socket = this.context.getSockets().get(0);
//        socket.send(data.getBytes(ZMQ.CHARSET), 0);
////        without a client, this is this blocking
//    }
}
