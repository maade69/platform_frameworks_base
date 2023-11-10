package grapheneos.tests;

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
import java.io.FileNotFoundException;
import java.io.InputStreamReader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


@RunWith(DeviceJUnit4ClassRunner.class)
public class ConnectivityTest extends BaseHostJUnit4Test {
    private static final int GRAPHENEOS_SERVER_SETTING = 0;
    private static final int STANDARD_SERVER_SETTING = 1;
    private static final int DISABLED_SETTING = 2;
    private static final String SYS_PROP = "persist.sys.connectivity_checks";
    private static final String WIFI_CMD = "cmd wifi set-wifi-enabled %s";
    private static final String FILTER_REQ_REGEXP = "PROBE_HTTP|PROBE_HTTPS";
    private static final String FILTER_DISABLED_REGEXP = "Validation disabled";

    enum serversGrapheneOS {
        HTTP("http://connectivitycheck.grapheneos.network/generate_204"),
        HTTPS("https://connectivitycheck.grapheneos.network/generate_204");

        private String server;

        serversGrapheneOS(String server) {
            this.server = server;
        }
    }

    enum serversStandard {
        HTTP("http://connectivitycheck.gstatic.com/generate_204"),
        HTTPS("https://www.google.com/generate_204");

        private String server;

        serversStandard(String server) {
            this.server = server;
        }
    }

    private void setProp(int setting) throws DeviceNotAvailableException {
        String cmd = String.format(
                "setprop %s %d",
                SYS_PROP, setting);
        getDevice().executeShellV2Command(cmd);
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
    }

    private final ArrayList parseLogcat(String regexp, int setting) throws Exception {
        final Pattern pattern = Pattern.compile(regexp);
        ArrayList<String> parsed = new ArrayList<String>();

        try (InputStreamSource logSource = getDevice().getLogcat()) {
            InputStreamReader streamReader = new InputStreamReader(logSource.createInputStream());
            BufferedReader logReader = new BufferedReader(streamReader);

            String line;
            while ((line = logReader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (setting != 2) {
                    if (matcher.find()) {
                        parsed.add(line);
                        if (parsed.toString().contains("PROBE_HTTP") && parsed.toString().contains("PROBE_HTTPS")) {
                            break;
                        }
                    }
                } else {
                    if (matcher.find()) {
                        parsed.add(matcher.group(0));
                        break;
                    }
                }
            }
            return parsed;
        }
    }

    private final boolean checkParsed(ArrayList matched, int setting) {
        boolean result = switch (setting) {
            case 0 ->
                    matched.get(0).toString().contains(serversGrapheneOS.HTTP.server) && matched.get(1).toString().contains(serversGrapheneOS.HTTPS.server);
            case 1 ->
                    matched.get(0).toString().contains(serversStandard.HTTP.server) && matched.get(1).toString().contains(serversStandard.HTTPS.server);
            case 2 -> matched.get(0).toString().equals(FILTER_DISABLED_REGEXP);
            default -> false;
        };
        return result;
    }

    private void baseTest(int setting) throws Exception {
        toggleWifi("disabled");
        setProp(setting);
        getDevice().clearLogcat();
        toggleWifi("enabled");
        RunUtil.getDefault().sleep(10000); // 10s
        assertTrue(getDevice().checkConnectivity());
    }

    @Test
    public void testGrapheneOSServer() throws Exception {
        baseTest(GRAPHENEOS_SERVER_SETTING);
        assertTrue(checkParsed(parseLogcat(FILTER_REQ_REGEXP, GRAPHENEOS_SERVER_SETTING), GRAPHENEOS_SERVER_SETTING));
    }

    @Test
    public void testStandardServer() throws Exception {
        baseTest(STANDARD_SERVER_SETTING);
        assertTrue(checkParsed(parseLogcat(FILTER_REQ_REGEXP, STANDARD_SERVER_SETTING), STANDARD_SERVER_SETTING));
    }

    @Test
    public void testDisabled() throws Exception {
        baseTest(DISABLED_SETTING);
        assertTrue(checkParsed(parseLogcat(FILTER_DISABLED_REGEXP, DISABLED_SETTING), DISABLED_SETTING));
    }
}