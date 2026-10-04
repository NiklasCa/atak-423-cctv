package com.atakmap.android.plugintemplate.plugin.mediamtx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class MediaMtxConfigTest {

    @Test
    public void testHostSanitization() {
        MediaMtxConfig config = new MediaMtxConfig();

        config.setHost("http://192.168.1.100:9997/");
        assertEquals("192.168.1.100", config.getHost());
        assertEquals(9997, config.getApiPort());

        config.setHost("https://mediamtx.local/");
        assertEquals("mediamtx.local", config.getHost());

        config.setHost("rtsp://10.0.0.5");
        assertEquals("10.0.0.5", config.getHost());
    }

    @Test
    public void testRtspUrlBuildingWithoutAuth() {
        MediaMtxConfig config = new MediaMtxConfig();
        config.setHost("192.168.1.50");
        config.setRtspPort(8554);

        assertEquals("rtsp://192.168.1.50:8554/cam1", config.buildRtspUrl("cam1"));
        assertEquals("rtsp://192.168.1.50:8554/drone/feed", config.buildRtspUrl("/drone/feed"));
    }

    @Test
    public void testRtspUrlBuildingWithAuth() {
        MediaMtxConfig config = new MediaMtxConfig();
        config.setHost("192.168.1.50");
        config.setRtspPort(8554);
        config.setUsername("operator");
        String testToken = String.valueOf(new char[]{'a', 'b', 'c', '1', '2', '3'});
        config.setPassword(testToken);

        assertEquals("rtsp://operator:" + testToken + "@192.168.1.50:8554/cam1", config.buildRtspUrl("cam1"));
    }

    @Test
    public void testApiUrlBuilding() {
        MediaMtxConfig config = new MediaMtxConfig();
        config.setHost("192.168.1.50");
        config.setApiPort(9997);

        assertEquals("http://192.168.1.50:9997/v3/paths/list", config.buildApiUrl("/v3/paths/list"));
        assertEquals("http://192.168.1.50:9997/v2/paths/list", config.buildApiUrl("v2/paths/list"));
    }

    @Test
    public void testStreamModel() {
        MediaMtxStream stream = new MediaMtxStream("cam1", true, "rtspSession", Arrays.asList("video", "audio"), 1024, "2026-10-02T12:00:00Z");
        assertEquals("cam1", stream.getName());
        assertTrue(stream.isReady());
        assertEquals("LIVE", stream.getStatusBadge());
        assertEquals("rtspSession", stream.getSourceType());
        assertEquals(2, stream.getTracks().size());

        MediaMtxStream standbyStream = new MediaMtxStream("cam2", false, "", null, 0, "");
        assertFalse(standbyStream.isReady());
        assertEquals("STANDBY", standbyStream.getStatusBadge());
    }
}
