package connect.vnc.android.websockify;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.PrintStream;


public class WebSockify {
    private WebsockifyServer wss;

    @Option(name = "--help", usage = "show this help message and quit")
    private boolean showHelp = false;

    @Option(name = "--enable-ssl", usage = "enable SSL")
    private boolean enableSSL = false;

    @Option(name = "--ssl-only", usage = "disallow non-encrypted connections")
    private boolean requireSSL = false;

    @Option(name = "--dir", usage = "run webserver on same port. Serve files from specified directory.")
    private String webDirectory = null;

    @Option(name = "--keystore", usage = "path to a java keystore file. Required for SSL.")
    private String keystore = null;

    @Option(name = "--keystore-password", usage = "password to the java keystore file. Required for SSL.")
    private String keystorePassword = null;

    @Option(name = "--keystore-key-password", usage = "password to the private key in the java keystore file. If not specified the keystore-password value will be used.")
    private String keystoreKeyPassword = null;

    @Option(name = "--direct-proxy-timeout", usage = "connection timeout before a direct proxy connection is established in milliseconds. Default is 5000 (5 seconds). With the VNC protocol the server sends the first message. This means that a client that wants a direct proxy connection will connect and not send a message. WebSockify will wait the specified number of milliseconds for an incoming connection to send a message. If no message is recieved it initiates a direct proxy connection. Setting this value too low will cause connection attempts that aren't direct proxy connections to fail. Set this to 0 to disable direct proxy connections.")
    private int directProxyTimeout = 5000;

    @Argument(index = 0, metaVar = "source_port", usage = "(required) local port the websockify server will listen on", required = true)
    private int sourcePort;

    @Argument(index = 1, metaVar = "target_host", usage = "(required) host the websockify server will proxy to", required = true)
    private String targetHost;

    @Argument(index = 2, metaVar = "target_port", usage = "(required) port the websockify server will proxy to", required = true)
    private int targetPort;

    private CmdLineParser parser;

    public WebSockify() {
        parser = new CmdLineParser(this);
    }

    public void printUsage(PrintStream out) {
        out.println("Usage:");
        out.println(" java -jar websockify.jar [options] source_port target_addr target_port");
        out.println();
        out.println("Options:");
        parser.printUsage(out);
        out.println();
        out.println("Example:");
        out.println(" java -jar websockify.jar 5900 server.example.net 5900");
    }

    public static void main(String[] args) throws Exception {
        new WebSockify().doMain(args);
    }

    public void closeSocket() {
        wss.close();
    }

    public void doMain(String[] args) throws Exception {
        parser.setUsageWidth(80);

        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            printUsage(System.err);
            return;
        }

        if (showHelp) {
            printUsage(System.out);
            return;
        }

        WebsockifyServer.SSLSetting sslSetting = WebsockifyServer.SSLSetting.OFF;
        if (requireSSL) sslSetting = WebsockifyServer.SSLSetting.REQUIRED;
        else if (enableSSL) sslSetting = WebsockifyServer.SSLSetting.ON;

        if (sslSetting != WebsockifyServer.SSLSetting.OFF) {
            if (keystore == null || keystore.isEmpty()) {
                System.err.println("No keystore specified.");
                printUsage(System.err);
                System.exit(1);
            }

            if (keystorePassword == null || keystorePassword.isEmpty()) {
                System.err.println("No keystore password specified.");
                printUsage(System.err);
                System.exit(1);
            }

            if (keystoreKeyPassword == null || keystoreKeyPassword.isEmpty()) {
                keystoreKeyPassword = keystorePassword;
            }

            try {
                WebsockifySslContext.validateKeystore(keystore, keystorePassword, keystoreKeyPassword);
            } catch (Exception e) {
                System.err.println("Error validating keystore: " + e.getMessage());
                printUsage(System.err);
                System.exit(2);
            }
        }

        System.out.println(
                "WebSockify Proxying *:" + sourcePort + " to " +
                        targetHost + ':' + targetPort + " ...");
        if (sslSetting != WebsockifyServer.SSLSetting.OFF)
            System.out.println("SSL is " + (sslSetting == WebsockifyServer.SSLSetting.REQUIRED ? "required." : "enabled."));

        PortUnificationHandler.setConnectionToFirstMessageTimeout(directProxyTimeout);

        wss = new WebsockifyServer();
        wss.connect(sourcePort, targetHost, targetPort, sslSetting, keystore, keystorePassword, keystoreKeyPassword, webDirectory);

    }

}
