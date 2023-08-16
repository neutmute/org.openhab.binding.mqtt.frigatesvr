/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.mqtt.frigatesvr.internal.servlet.streams;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRCommonConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link StreamType} encapsulates each individual type of stream
 *
 * @author Dr J Gow - initial contribution
 */
@NonNullByDefault
public class StreamTypeBase {

    private final Logger logger = LoggerFactory.getLogger(StreamTypeBase.class);
    protected FFmpegManager ffHelper = new FFmpegManager();
    protected boolean isStreamRunning = false;
    protected int hitCount = 0;
    public String pathfromFF = "";
    public String readerPath = "";
    private int keepalive_delay = 2;
    protected frigateSVRCommonConfiguration config;
    protected boolean startOnLoad = true;

    @SuppressWarnings("serial")
    private static final Map<String, String> mimeExt = new HashMap<String, String>() {
        {
            put("mpd", "application/dash+xml");
            put("mp4", "video/mp4");
            put("m4v", "video");
            put("m4s", "video/iso.segment");
            put("m4a", "audio/mp4");
            put("m3u8", "application/x-mpegURL");
            put("ts", "video/MP2T");
        }
    };

    public StreamTypeBase(String baseURL, String ffBinary, String URLtoFF, String readerPath,
            frigateSVRCommonConfiguration config) {
        this.readerPath = readerPath;
        this.config = config;
    }

    /////////////////////////////////////////////////////////////////////////
    // GetMime
    //
    // Return the mime type for a file with a given extension, or unknown

    private static String GetMime(String fn) {
        Logger logger = LoggerFactory.getLogger(StreamTypeBase.class);

        String ext = fn.substring(fn.lastIndexOf('.') + 1);
        logger.info("extension {}", ext);
        if (!ext.equals("")) {
            if (mimeExt.containsKey(ext)) {
                logger.info("mime type {}", mimeExt.get(ext));
                return (@NonNull String) (mimeExt.get(ext));
            }
        }
        return "application/octet-stream";
    }

    /////////////////////////////////////////////////////////////////////////
    // ServerReady
    //
    // Called when the server is ready - we can use this to start streams
    // if the stream producer start on demand is disabled.

    public void ServerReady() {
        if (this.startOnLoad) {
            StartStreams();
        }
    }

    /////////////////////////////////////////////////////////////////////////
    // StartStreams
    //
    // To start the streams, if we are not already running. This could come
    // in on multiple contexts - so use a context lock

    public synchronized void StartStreams() {

        // if our ffmpeg process is already running, we need
        // do nothing.

        if (!this.ffHelper.isRunning() && !isStreamRunning) {

            // otherwise we need to start it and wait for it to start

            this.ffHelper.StartStream();

            // Right then: now wait until ffmpeg has started spitting output
            // and that it is available, otherwise we may get browser error
            // messages. Need to have this online before the browser timeouts
            // This can take some time. While this code doesn't actually block,
            // it could sit in the loop up to 15 seconds in order to get
            // ffmpeg started.
            // The check for actual output is stream type specific.

            int count = 0;
            do {
                logger.info("waiting 1000ms for stream to appear");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
                if (this.CheckStarted()) {
                    logger.info("ffmpeg stream confirmed started");

                    // guarantees we always wait one timeout interval
                    // once the stream is marked 'running'

                    hitCount = 1;
                    isStreamRunning = true;

                    break;
                } else {
                    if (count++ == 15) {
                        logger.warn("ffmpeg start failed");
                        this.StopStreams();
                        break;
                    }
                }
            } while (true);
        }
    }

    /////////////////////////////////////////////////////////////////////////
    // CheckStarted
    //
    // This member function is overloaded per stream type to return true
    // as soon as output has been generated.

    public boolean CheckStarted() {
        return false;
    }

    /////////////////////////////////////////////////////////////////////////
    // StopStreams
    //
    // Called by the servlet to ensure the stream is stopped and cleaned up.

    public synchronized void StopStreams() {
        logger.info("StopStreams called");
        isStreamRunning = false;
        this.ffHelper.StopStream();
    }

    /////////////////////////////////////////////////////////////////////////
    // Cleanup
    //
    // Called by the servlet to remove stream environments.

    public synchronized void Cleanup() {
        this.StopStreams();
        this.ffHelper.Cleanup();
    }

    /////////////////////////////////////////////////////////////////////////
    // PokeMe
    //
    // Automatically called from the keepalive. Verify the hitcount. If we get
    // no hits between keepalives, then shut down the stream. However, it can
    // take a while for the ffmpeg producer to start loading files, so
    // do not check the hit count unless the ffmpeg process has written
    // the playlist.

    public synchronized void PokeMe() {
        this.ffHelper.PokeMe();
        if ((this.isStreamRunning == true) && !startOnLoad) {
            if (--keepalive_delay == 0) {
                logger.info("stream is running ({})", this.getClass().getSimpleName());
                // no-one has requested the stream between now and the last
                // keepalive. Assume we're not wanted, so go and eat worms.
                if (hitCount == 0) {
                    logger.info("no further requestors; shutting down stream");
                    StopStreams();
                } else {
                    logger.info("hitcount = {}, stream continuing", hitCount);
                }
                keepalive_delay = 2;
            }
        }
        hitCount = 0;
    }

    /////////////////////////////////////////////////////////////////////////
    // canPost
    //
    // Must return true if the post is valid for this stream type

    public boolean canPost(String pathInfo) {
        return false;
    }

    ////////////////////////////////////////////////////////////////////////
    // canAccept
    //
    // Must return true if the stream can accept the GET request.

    public boolean canAccept(String pathInfo) {
        return false;
    }

    ////////////////////////////////////////////////////////////////////////
    // Poster
    //
    // Callback in response to a valid POST response sent to this endpoint

    public void Poster(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws IOException {
    }

    //////////////////////////////////////////////////////////////////////////
    // Getter
    //
    // Callback in response to a GET

    public void Getter(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws IOException {
        resp.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    /////////////////////////////////////////////////////////////////////////
    // SendFile
    //
    // Send a file in response.

    protected void SendFile(HttpServletResponse response, String filename, String contentType) throws IOException {

        String mimeType;
        if (contentType.equals("")) {
            mimeType = GetMime(filename);
        } else {
            mimeType = contentType;
        }

        logger.info("serving file {} content type {}", filename, mimeType);

        File file = new File(filename);
        if (!file.exists()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            logger.info("file {} not found", filename);
            return;
        }

        response.setBufferSize((int) file.length());
        response.setContentType(mimeType);

        // Ensure headers are set to inform the client
        // that files should not be cached. Otherwise we will get old stream
        // data over and over.

        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Expose-Headers", "*");
        response.setHeader("Content-Length", String.valueOf(file.length()));
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        FileInputStream fs = new FileInputStream(file);
        fs.transferTo(response.getOutputStream());
        fs.close();
    }
}