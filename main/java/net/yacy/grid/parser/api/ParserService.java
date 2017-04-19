/**
 *  ParserService
 *  Copyright 1.4.2017 by Michael Peter Christen, @0rb1t3r
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.grid.loader.api.parser;

import java.io.IOException;
import java.io.InputStream;

import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;
import org.jwat.common.HeaderLine;
import org.jwat.common.HttpHeader;
import org.jwat.warc.WarcConstants;
import org.jwat.warc.WarcReader;
import org.jwat.warc.WarcReaderFactory;
import org.jwat.warc.WarcRecord;

import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.util.ByteBuffer;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.retrieval.Response;
import net.yacy.document.Document;
import net.yacy.document.Parser.Failure;
import net.yacy.document.TextParser;
import net.yacy.grid.http.APIHandler;
import net.yacy.grid.http.JSONAPIHandler;
import net.yacy.grid.http.JSONObjectAPIHandler;
import net.yacy.grid.http.Query;
import net.yacy.grid.http.ServiceResponse;
import net.yacy.grid.io.assets.Asset;
import net.yacy.grid.io.assets.StorageFactory;
import net.yacy.grid.mcp.Data;
import net.yacy.grid.tools.MultiProtocolURL;
import net.yacy.server.http.ChunkedInputStream;

/**
 * test: call
 * http://127.0.0.1:8500/yacy/grid/yacyparser/parser.json?url=http://yacy.net
 */
public class ParserService extends JSONObjectAPIHandler implements APIHandler {

    private static final long serialVersionUID = 8578474303031749879L;
    public static final String NAME = "parser";
    private final static byte[] EMPTY_ASSET = new byte[0];
    
    @Override
    public String getAPIPath() {
        return "/yacy/grid/yacyparser/" + NAME + ".json";
    }
    
    @Override
    public ServiceResponse serviceImpl(Query call, HttpServletResponse response) {

        // load a WARC from the sourcepath and store json flat file into targetpath
        String sourcepath = call.get("sourcepath", "");
        byte[] sourceasset = call.get("sourceasset", EMPTY_ASSET);
        String targetpath = call.get("targetpath", "");
        if (sourcepath.length() > 0) {
            try {
                Asset<byte[]> asset = Data.gridStorage.load(sourcepath);
                sourceasset = asset.getPayload();
            } catch (IOException e) {
                Data.logger.error(e.getMessage(), e);
            }
        }
        
        // compute targetasset
        byte[] targetasset = new byte[0];
        
        // store result and return success
        JSONObject json = new JSONObject(true);
        if (targetasset.length > 0 && targetpath.length() > 0) {
            try {
                StorageFactory<byte[]> factory = Data.gridStorage.store(targetpath, targetasset);
                String url = factory.getConnectionURL();
                json.put(JSONAPIHandler.SUCCESS_KEY, true);
                if (url != null) json.put(JSONAPIHandler.SERVICE_KEY, url);
            } catch (IOException e) {
                json.put(JSONAPIHandler.SUCCESS_KEY, false);
                json.put(JSONAPIHandler.COMMENT_KEY, e.getMessage());
            }
        } else {
            json.put(JSONAPIHandler.SUCCESS_KEY, false);
            json.put(JSONAPIHandler.COMMENT_KEY, "the request must contain a sourcepath or sourceasset and a targetpath");
        }
        return new ServiceResponse(json);
    }
    
    /**
     * WARC importer code from net.yacy.document.importer.WarcImporter.java 
     * @param f
     * @throws IOException
     */
    public void indexWarcRecords(InputStream f) throws IOException {

        byte[] content;
        int cnt = 0;

        WarcReader localwarcReader = WarcReaderFactory.getReader(f);
        WarcRecord wrec = localwarcReader.getNextRecord();
        while (wrec != null) {

            HeaderLine hl = wrec.getHeader(WarcConstants.FN_WARC_TYPE);
            if (hl != null && hl.value.equals(WarcConstants.RT_RESPONSE)) { // filter responses

                hl = wrec.getHeader(WarcConstants.FN_WARC_TARGET_URI);
                MultiProtocolURL location = new MultiProtocolURL(hl.value);

                HttpHeader http = wrec.getHttpHeader();

                if (http != null && http.statusCode == 200) { // process http response header OK (status 200)

                    if (TextParser.supportsMime(http.contentType) == null) { // check availability of parser

                        InputStream istream = wrec.getPayloadContent();
                        hl = http.getHeader(HeaderFramework.TRANSFER_ENCODING);
                        if (hl != null && hl.value.contains("chunked")) {
                            // because chunked stream.read doesn't read source fully, make sure all chunks are read
                            istream = new ChunkedInputStream(istream);
                            final ByteBuffer bbuffer = new ByteBuffer();
                            int c;
                            while ((c = istream.read()) >= 0) {
                                bbuffer.append(c);
                            }
                            content = bbuffer.getBytes();
                        } else {
                            content = new byte[(int) http.getPayloadLength()];
                            istream.read(content, 0, content.length);
                        }
                        istream.close();

                        RequestHeader requestHeader = new RequestHeader();

                        ResponseHeader responseHeader = new ResponseHeader(http.statusCode);
                        for (HeaderLine hx : http.getHeaderList()) { // include all original response headers for parser
                            responseHeader.put(hx.name, hx.value);
                        }

                        final Request request = new Request(
                                null,
                                location,
                                requestHeader.referer() == null ? null : requestHeader.referer(),
                                "warc",
                                responseHeader.lastModified(),
                                0);

                        final Response response = new Response(
                                request,
                                requestHeader,
                                responseHeader,
                                false,
                                content
                        );

                        Document[] docs = new Document[0];
                        try {
                            docs = TextParser.parseSource(location, responseHeader.get(HeaderFramework.CONTENT_ENCODING, ""), "UTF-8", null, 0, 0, content);
                        } catch (Failure e) {
                            e.printStackTrace();
                        }
                        
                        // debug code
                        if (docs != null) {
                            for (Document doc: docs) {
                                System.out.println(doc.dc_title());
                                
                            }
                        }
                        //Switchboard.getSwitchboard().toIndexer(response);
                        cnt++;
                    }
                }
            }
            wrec = localwarcReader.getNextRecord();
        }
        localwarcReader.close();
        Data.logger.info("WarcImporter", "Indexed " + cnt + " documents");
    }
    
}
