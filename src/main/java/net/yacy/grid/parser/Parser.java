/**
 *  Parser
 *  Copyright 1.04.2017 by Michael Peter Christen, @0rb1t3r
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

package net.yacy.grid.parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

import javax.servlet.Servlet;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import ai.susi.mind.SusiAction;
import ai.susi.mind.SusiThought;
import net.yacy.grid.YaCyServices;
import net.yacy.grid.io.assets.Asset;
import net.yacy.grid.io.index.WebMapping;
import net.yacy.grid.io.messages.MessageContainer;
import net.yacy.grid.mcp.Data;
import net.yacy.grid.mcp.MCP;
import net.yacy.grid.mcp.Service;
import net.yacy.grid.parser.api.ParserService;
import net.yacy.grid.tools.Digest;


public class Parser {

    private final static YaCyServices SERVICE = YaCyServices.parser;
    private final static String DATA_PATH = "data";
    private final static String APP_PATH = "parser";
 
    // define services
    @SuppressWarnings("unchecked")
    public final static Class<? extends Servlet>[] PARSER_SERVICES = new Class[]{
            // information services
            ParserService.class
    };
    
    /*
     * test this with
     * curl -X POST -F "message=@job.json" -F "serviceName=parser" -F "queueName=yacyparser" http://yacygrid.com:8100/yacy/grid/mcp/messages/send.json
     * 
{
  "metadata": {
    "process": "yacy_grid_parser",
    "count": 1
  },
  "data": [{"collection": "test"}],
  "actions": [{
    "type": "parser",
    "queue": "yacyparser",
    "sourceasset": "test3/yacy.net.warc.gz",
    "targetasset": "test3/yacy.net.text.jsonlist",
    "targetgraph": "test3/yacy.net.graph.jsonlist"
  },{
    "type": "indexer",
    "queue": "elasticsearch",
    "targetindex": "webindex",
    "targettype" : "common",
    "sourceasset": "test3/yacy.net.text.jsonlist"
  },{
    "type": "crawler",
    "queue": "webcrawler",
    "sourceasset": "test3/yacy.net.graph.jsonlist"
  }]
}
     */
    public static class BrokerListener extends Thread {
        public boolean shallRun = true;
        
        @Override
        public void run() {
            while (shallRun) {
                if (Data.gridBroker == null) {
                    try {Thread.sleep(1000);} catch (InterruptedException ee) {}
                } else try {
                    MessageContainer<byte[]> mc = Data.gridBroker.receive(YaCyServices.parser.name(), YaCyServices.parser.getDefaultQueue(), 10000);
                    if (mc == null || mc.getPayload() == null) continue;
                    JSONObject json = new JSONObject(new JSONTokener(new String(mc.getPayload(), StandardCharsets.UTF_8)));
                    SusiThought process = new SusiThought(json);
                    List<SusiAction> actions = process.getActions();
                    actionloop: for (int ac = 0; ac < actions.size(); ac++) {
                        SusiAction a = actions.get(ac);
                        String type = a.getStringAttr("type");
                        String queue = a.getStringAttr("queue");
                        if (type == null || type.length() == 0 || queue == null || queue.length() == 0) {
                            Data.logger.info("bad message in queue, continue");
                            continue actionloop;
                        }
                        if (!type.equals(YaCyServices.parser.name())) {
                            Data.logger.info("wrong message in queue: " + type + ", continue");
                            try {
                                loadNextAction(a, process.getData()); // put that into the correct queue
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }
                            continue actionloop;
                        }

                        boolean processed = processAction(a);
                        if (processed) {
                            // send next embedded action(s) to queue
                            JSONArray embeddedActions = a.toJSONClone().getJSONArray("actions");
                            for (int j = 0; j < embeddedActions.length(); j++) {
                                loadNextAction(new SusiAction(embeddedActions.getJSONObject(j)), process.getData());
                            }    
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    try {Thread.sleep(1000);} catch (InterruptedException ee) {}
                }
            }
        }
        public void terminate() {
            this.shallRun = false;
        }
    }
    
    public static boolean processAction(SusiAction a) {

        String sourceasset_path = a.getStringAttr("sourceasset");
        String targetasset_path = a.getStringAttr("targetasset");
        String targetgraph_path = a.getStringAttr("targetgraph");
        boolean elastic = a.getBooleanAttr("bulk");
        if (targetasset_path == null || targetasset_path.length() == 0 ||
            sourceasset_path == null || sourceasset_path.length() == 0) return false;
        
        InputStream sourceStream = null;
        try {
            Asset<byte[]> asset = Data.gridStorage.load(sourceasset_path);
            byte[] source = asset.getPayload();
            sourceStream = new ByteArrayInputStream(source);
            if (sourceasset_path.endsWith(".gz")) sourceStream = new GZIPInputStream(sourceStream);

            // compute parsed documents
            JSONArray parsedDocuments = ParserService.indexWarcRecords(sourceStream);
            StringBuffer targetasset_object = new StringBuffer(2048);
            StringBuffer targetgraph_object = targetgraph_path != null && targetgraph_path.length() > 0 ? new StringBuffer(2048) : null;
            for (int i = 0; i < parsedDocuments.length(); i++) {
                JSONObject docjson = parsedDocuments.getJSONObject(i);
                if (elastic) {
                    String url = docjson.getString(WebMapping.url_s.name());
                    String id = Digest.encodeMD5Hex(url);
                    JSONObject bulkjson = new JSONObject().put("index", new JSONObject().put("_id", id));
                    targetasset_object.append(bulkjson.toString(0)).append("\n");
                    if (targetgraph_object != null) {
                        targetgraph_object.append(bulkjson.toString(0)).append("\n");
                    }
                }
                targetasset_object.append(docjson.toString(0)).append("\n");
                if (targetgraph_object != null) {
                    targetgraph_object.append(ParserService.extractGraph(docjson).toString(0)).append("\n");
                }
            }

            Data.gridStorage.store(targetasset_path, targetasset_object.toString().getBytes(StandardCharsets.UTF_8));
            Data.gridStorage.store(targetgraph_path, targetgraph_object.toString().getBytes(StandardCharsets.UTF_8));
            Data.logger.info("processed message from queue and stored asset " + targetasset_path);

            return true;
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public static void loadNextAction(SusiAction action, JSONArray data) throws UnsupportedOperationException, IOException {
        String type = action.getStringAttr("type");
        if (type == null || type.length() == 0) throw new UnsupportedOperationException("missing type in action");
        String queue = action.getStringAttr("queue");
        if (queue == null || queue.length() == 0) throw new UnsupportedOperationException("missing queue in action");

        // create a new Thought and push it to the next queue
        JSONObject nextProcess = new JSONObject()
                .put("data", data)
                .put("actions", new JSONArray().put(action.toJSONClone()));
        byte[] b = nextProcess.toString().getBytes(StandardCharsets.UTF_8);
        Data.gridBroker.send(type, queue, b);
    }
    
    public static void main(String[] args) {
        BrokerListener brokerListener = new BrokerListener();
        brokerListener.start();
        List<Class<? extends Servlet>> services = new ArrayList<>();
        services.addAll(Arrays.asList(MCP.MCP_SERVICES));
        services.addAll(Arrays.asList(PARSER_SERVICES));
        Service.runService(SERVICE, DATA_PATH, APP_PATH, null, services);
    }
    
}
