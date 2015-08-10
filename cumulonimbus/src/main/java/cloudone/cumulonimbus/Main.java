package cloudone.cumulonimbus;

import cloudone.ApplicationInfo;
import cloudone.C1Services;
import cloudone.RuntimeInfo;
import cloudone.internal.ApplicationInfoImpl;
import cloudone.internal.RuntimeInfoImpl;
import org.apache.commons.cli.HelpFormatter;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.CountDownLatch;


/**
 * Main class for Cumulonimbus application.
 *
 * @author Martin Mares (martin.mares at oracle.com)
 */
public final class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private static final int DEFAULT_PORT = 4242;

    private final CumulonimbusApp cumulonimbusApp;
    private final ApplicationInfo applicationInfo;
    private final CountDownLatch stopLatch = new CountDownLatch(1);

    private Main(CumulonimbusApp cumulonimbusApp) {
        this.cumulonimbusApp = cumulonimbusApp;
        ApplicationInfo ai = null;
        for (ApplicationInfo applicationInfo : C1Services.getInstance().getRuntimeInfo().getApplicationInfos()) {
            if (applicationInfo.getApplication() == cumulonimbusApp) {
                ai = applicationInfo;
                break;
            }
        }
        if (ai == null) {
            throw new RuntimeException("Cannot find application info for Cumulonimbus app!");
        }
        if (ai.getPort() < 0) {
            ((ApplicationInfoImpl) ai).setPort(DEFAULT_PORT);
        }
        this.applicationInfo = ai;
    }

    public void stop() {
        stopLatch.countDown();
    }

    private void run() throws Exception {
        if (C1Services.getInstance().getRuntimeInfo().getCommandLine().hasOption('h')) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("CloudOne Cumulonimbus Application", C1Services.getInstance().getRuntimeInfo().getCmdlOptions());
            return;
        }
        //Start Cumulonimbus
        cumulonimbusApp.init();
        LOGGER.info("STARTING: " + applicationInfo.getName());
        final ResourceConfig resourceConfig = ResourceConfig.forApplication(cumulonimbusApp);
        final URI uri = URI.create("http://localhost:" + applicationInfo.getPort() + "/");
        final HttpServer server = GrizzlyHttpServerFactory.createHttpServer(uri, resourceConfig);
        Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    LOGGER.info("--------------- SHUTDOWN: " + applicationInfo.getName() + " ---------------");
                    server.shutdown();
                    applicationInfo.getApplication().shutDown();
                }
            });
        cumulonimbusApp.started();
        LOGGER.info("--------------- " + applicationInfo.getName() + " is RUNNING on port " + applicationInfo.getPort() + " ---------------");
        //Wait for stop signal on the application
        stopLatch.await();
    }

    public static void main(String[] args) {
        try {
            CumulonimbusApp app = new CumulonimbusApp();
            (new RuntimeInfoImpl.Builder())
                    .addApplication(app)
                    .processCommandLineArgs(args)
                    .build();
            Main instance = new Main(app);
            instance.run();
        } catch (Exception exc) {
            LOGGER.error("General exception in main thread!", exc);
        }
    }
}
