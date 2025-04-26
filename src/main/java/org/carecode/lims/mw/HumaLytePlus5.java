package org.carecode.lims.mw;

import com.fazecast.jSerialComm.SerialPort;
import com.google.gson.Gson;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.carecode.lims.libraries.*;

public class HumaLytePlus5 {
    public static final Logger logger = LogManager.getLogger("SmartLytePlusLogger");
    public static MiddlewareSettings middlewareSettings;
    public static LISCommunicator limsUtils;
    public static boolean testingLis = false;  // Indicates whether to run test before starting the server

    public static void main(String[] args) {
        logger.info("SmartLytePlusMiddleware started at: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        loadSettings();

        if (middlewareSettings != null) {
            limsUtils = new LISCommunicator(logger, middlewareSettings);

            if (testingLis) {
                logger.info("Testing LIS started");
                testLis();  // Perform the test method before starting the server
                logger.info("Testing LIS Ended. System will now shutdown.");
                System.exit(0);
            }

            startServer();  // Start the server if no testing or after testing
        } else {
            logger.error("Failed to load settings.");
        }
    }

    public static void testLis() {
        logger.info("Starting LIMS test process...");
        String filePath = "response.txt";  // Path to the test data file

        try {
            String responseContent = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
            Map<String, String> params = limsUtils.parseQueryParams(responseContent);
            DataBundle dataBundle = limsUtils.createDataBundleFromParams(params);

            limsUtils.pushResults(dataBundle);
            logger.info("Test results sent to LIMS successfully.");
        } catch (IOException e) {
            logger.error("Failed to read test data from file: " + filePath, e);
        } catch (Exception e) {
            logger.error("An unexpected error occurred during the LIMS test process.", e);
        }
    }

    public static void loadSettings() {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader("config.json")) {
            middlewareSettings = gson.fromJson(reader, MiddlewareSettings.class);
            logger.info("Settings loaded from config.json");
        } catch (IOException e) {
            logger.error("Failed to load settings from config.json", e);
        }
    }
    
    public void checkAnelizerPoer() {
        SerialPort[] ports = SerialPort.getCommPorts();

        if (ports.length == 0) {
            System.out.println("No serial ports found.");
            return;
        }

        System.out.println("Available ports:");
        for (int i = 0; i < ports.length; i++) {
            System.out.println((i + 1) + ": " + ports[i].getSystemPortName());
        }

//        SerialPort analyzerPort = ports[0]; // Change index if needed
        SerialPort analyzerPort = SerialPort.getCommPort("COM1"); // Replace with COM1 or COM2

        analyzerPort.setBaudRate(19200);
        analyzerPort.setNumDataBits(8);
        analyzerPort.setNumStopBits(SerialPort.ONE_STOP_BIT);
        analyzerPort.setParity(SerialPort.NO_PARITY);

        if (!analyzerPort.openPort()) {
            System.out.println("Failed to open port.");
            return;
        }

        System.out.println("Listening for data on: " + analyzerPort.getSystemPortName());

        byte[] buffer = new byte[1024];

        while (true) {
            if (analyzerPort.bytesAvailable() > 0) {
                int numRead = analyzerPort.readBytes(buffer, buffer.length);
                String received = new String(buffer, 0, numRead);
                System.out.print(received);
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }

        analyzerPort.closePort();
    }

    public static void startServer() {
        try {
            int port = middlewareSettings.getAnalyzerDetails().getHostPort();
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new ResponseHandler(logger, limsUtils));
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            logger.info("Server started on port " + port);
        } catch (IOException e) {
            logger.error("Failed to start the server", e);
        }
    }
    
}
