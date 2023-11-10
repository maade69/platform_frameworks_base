package grapheneos.test;

import com.android.tradefed.util.RunUtil;

import com.android.tradefed.device.INativeDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.device.DeviceNotAvailableException;

import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.result.InputStreamSource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


@RunWith(DeviceJUnit4ClassRunner.class)
public class ConnectivityCheckSettingTest extends BaseHostJUnit4Test {
    private static final int GRAPHENEOS_SERVER_SETTING = 0;
    private static final int STANDARD_SERVER_SETTING = 1;
    private static final int DISABLED_SETTING = 2;

    private static final String SYS_PROP = "persist.sys.connectivity_checks";
    private static final String WIFI_CMD = "cmd wifi set-wifi-enabled %s";
    private static final String LOG_TAG = "NetworkMonitor";
    private static final String FILTER_REQ_REGEXP = "PROBE_HTTP|PROBE_HTTPS";
    private static final String FILTER_DISABLED_REGEXP = "Validation disabled";

    enum ConnectivityCheckServer {
        GRAPHENEOS("http://connectivitycheck.grapheneos.network/generate_204", "https://connectivitycheck.grapheneos.network/generate_204"),
        STANDARD("http://connectivitycheck.gstatic.com/generate_204", "https://www.google.com/generate_204");

        public final String httpUrl;
        public final String httpsUrl;

        ConnectivityCheckServer(String httpUrl, String httpsUrl) {
            this.httpUrl = httpUrl;
            this.httpsUrl = httpsUrl;
        }
    }

    private void toggleWifi(String state) throws DeviceNotAvailableException {
        String cmd = String.format(WIFI_CMD, state);
        getDevice().executeShellV2Command(cmd);
    }

    @Before
    public void setUp() throws DeviceNotAvailableException {
        if (!getDevice().isAdbRoot()) {
            try {
                getDevice().enableAdbRoot();
            } finally {
                fail("adb root access is required");
            }
        }
        getDevice().clearLogcat();
    }

    private final ArrayList<String> parseLogcat(String regexp, int setting) throws Exception {
        final Pattern pattern = Pattern.compile(regexp);
        ArrayList<String> result = new ArrayList<String>();

        try (InputStreamSource logSource = getDevice().getLogcat()) {
            InputStreamReader streamReader = new InputStreamReader(logSource.createInputStream());
            BufferedReader logReader = new BufferedReader(streamReader);

            String line;
            while ((line = logReader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (setting != DISABLED_SETTING) {
                    if (matcher.find() && line.contains(LOG_TAG)) {
                        result.add(line);
                        if (result.toString().contains("PROBE_HTTP") && result.toString().contains("PROBE_HTTPS")) {
                            break;
                        }
                    }
                } else {
                    if (matcher.find()) {
                        result.add(matcher.group(0));
                        break;
                    }
                }
            }
            return result;
        }
    }

    private final boolean checkParsed(ArrayList<String> matched, int setting) {
        if (matched.size() <= 2) {
            boolean result = switch (setting) {
                case GRAPHENEOS_SERVER_SETTING ->
                        matched.get(0).contains(ConnectivityCheckServer.GRAPHENEOS.httpUrl) && matched.get(1).contains(ConnectivityCheckServer.GRAPHENEOS.httpsUrl);
                case STANDARD_SERVER_SETTING ->
                        matched.get(0).contains(ConnectivityCheckServer.STANDARD.httpUrl) && matched.get(1).contains(ConnectivityCheckServer.STANDARD.httpsUrl);
                case DISABLED_SETTING -> matched.get(0).equals(FILTER_DISABLED_REGEXP);
                default -> false;
            };
            return result;
        } else { return false; }
    }

    private boolean baseTest(int setting) throws Exception {
        toggleWifi("disabled");
        getDevice().setProperty(SYS_PROP, Integer.toString(setting));
        RunUtil.getDefault().sleep(1000); // 1s
        getDevice().clearLogcat();
        toggleWifi("enabled");
        RunUtil.getDefault().sleep(10000); // 10s
        
        return getDevice().checkConnectivity();
    }

    @Test
    public void testGrapheneOSServer() throws Exception {
        assertTrue(baseTest(GRAPHENEOS_SERVER_SETTING));
        assertTrue(checkParsed(parseLogcat(FILTER_REQ_REGEXP, GRAPHENEOS_SERVER_SETTING), GRAPHENEOS_SERVER_SETTING));
    }

    @Test
    public void testStandardServer() throws Exception {
        assertTrue(baseTest(STANDARD_SERVER_SETTING));
        assertTrue(checkParsed(parseLogcat(FILTER_REQ_REGEXP, STANDARD_SERVER_SETTING), STANDARD_SERVER_SETTING));
    }

    @Test
    public void testDisabled() throws Exception {
        assertTrue(baseTest(DISABLED_SETTING));
        assertTrue(checkParsed(parseLogcat(FILTER_DISABLED_REGEXP, DISABLED_SETTING), DISABLED_SETTING));
    }
}