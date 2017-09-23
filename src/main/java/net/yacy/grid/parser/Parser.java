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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

import javax.servlet.Servlet;

import org.json.JSONArray;
import org.json.JSONObject;

import ai.susi.mind.SusiAction;
import net.yacy.grid.YaCyServices;
import net.yacy.grid.io.assets.Asset;
import net.yacy.grid.io.index.WebMapping;
import net.yacy.grid.mcp.AbstractBrokerListener;
import net.yacy.grid.mcp.BrokerListener;
import net.yacy.grid.mcp.Data;
import net.yacy.grid.mcp.MCP;
import net.yacy.grid.mcp.Service;
import net.yacy.grid.parser.api.ParserService;
import net.yacy.grid.tools.Digest;
import net.yacy.grid.tools.JSONList;

public class Parser {

    private final static YaCyServices PARSER_SERVICE = YaCyServices.parser;
    private final static String DATA_PATH = "data";
 
    // define services
    @SuppressWarnings("unchecked")
    public final static Class<? extends Servlet>[] PARSER_SERVICES = new Class[]{
            // information services
            ParserService.class
    };
    
    /*
     * test this with
     * curl -X POST -F "message=@job.json" -F "serviceName=parser" -F "queueName=yacyparser" http://yacygrid.com:8100/yacy/grid/mcp/messages/send.json
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
    "targetgraph": "test3/yacy.net.graph.jsonlist",
    "actions": [
      {
      "type": "indexer",
      "queue": "elasticsearch",
      "targetindex": "webindex",
      "targettype" : "common",
      "sourceasset": "test3/yacy.net.text.jsonlist"
      },
      {
        "type": "crawler",
        "queue": "webcrawler",
        "sourceasset": "test3/yacy.net.graph.jsonlist"
      }
    ]
  }]
}
     */
    public static class ParserListener extends AbstractBrokerListener implements BrokerListener {

        public ParserListener(YaCyServices service) {
             super(service, Runtime.getRuntime().availableProcessors());
        }

        public boolean processAction(SusiAction action, JSONArray data) {
    
            String sourceasset_path = action.getStringAttr("sourceasset");
            String targetasset_path = action.getStringAttr("targetasset");
            String targetgraph_path = action.getStringAttr("targetgraph");
            if (targetasset_path == null || targetasset_path.length() == 0 ||
                sourceasset_path == null || sourceasset_path.length() == 0) return false;

            byte[] source = null;
            if (action.hasAsset(sourceasset_path)) {
            	source = action.getBinaryAsset(sourceasset_path);
            }
            if (source == null) try {
                Asset<byte[]> asset = Data.gridStorage.load(sourceasset_path);
                source = asset.getPayload();
            } catch (Throwable e) {
                e.printStackTrace();
                // if we do not get the payload from the storage, we look for attached data in the action
                Data.logger.warn("could not load asset: " + sourceasset_path, e);
                return false;
            }
            try{
                InputStream sourceStream = null;
                sourceStream = new ByteArrayInputStream(source);
                if (sourceasset_path.endsWith(".gz")) sourceStream = new GZIPInputStream(sourceStream);
    
                // compute parsed documents
                JSONArray parsedDocuments = ParserService.indexWarcRecords(sourceStream);
                JSONList targetasset_object = new JSONList();
                JSONList targetgraph_object = new JSONList();
                for (int i = 0; i < parsedDocuments.length(); i++) {
                    JSONObject docjson = parsedDocuments.getJSONObject(i);
                    
                    // create elasticsearch index line
                    String url = docjson.getString(WebMapping.url_s.name());
                    String id = Digest.encodeMD5Hex(url);
                    JSONObject bulkjson = new JSONObject().put("index", new JSONObject().put("_id", id));

                    // write web index
                    targetasset_object.add(bulkjson);
                    //docjson.put("_id", id);
                    targetasset_object.add(docjson);
                    
                    // write graph
                    if (targetgraph_object != null) {
                        targetgraph_object.add(bulkjson);
                        JSONObject graphjson = ParserService.extractGraph(docjson);
                        //graphjson.put("_id", id);
                        targetgraph_object.add(graphjson);
                    }
                }
                
                boolean storeToMessage = true; // debug version for now: always true TODO: set to false later
                try {
                	String targetasset = targetasset_object.toString();
                	Data.gridStorage.store(targetasset_path, targetasset.getBytes(StandardCharsets.UTF_8));
                } catch (Throwable ee) {
                	 ee.printStackTrace();
                     Data.logger.info("asset " + targetasset_path + " could not be stored, carrying the asset within the next action");
                     storeToMessage = true;
                }
                try {
                	String targetgraph = targetgraph_object.toString();
                	Data.gridStorage.store(targetgraph_path, targetgraph.getBytes(StandardCharsets.UTF_8));
                } catch (Throwable ee) {
                	Data.logger.info("asset " + targetgraph_path + " could not be stored, carrying the asset within the next action");
                	storeToMessage = true;
                }        
                // emergency storage to message
                if (storeToMessage) {
                	JSONArray actions = action.getEmbeddedActions();
                    actions.forEach(a -> {
                        new SusiAction((JSONObject) a).setJSONListAsset(targetasset_path, targetasset_object);
                        new SusiAction((JSONObject) a).setJSONListAsset(targetgraph_path, targetgraph_object);
                    });
                }
                Data.logger.info("processed message from queue and stored asset " + targetasset_path);
    
                return true;
            } catch (Throwable e) {
                e.printStackTrace();
                return false;
            }
        }
    }
        
    public static void main(String[] args) {
        // initialize environment variables
        List<Class<? extends Servlet>> services = new ArrayList<>();
        services.addAll(Arrays.asList(MCP.MCP_SERVICES));
        services.addAll(Arrays.asList(PARSER_SERVICES));
        Service.initEnvironment(PARSER_SERVICE, services, DATA_PATH);

        // start listener
        BrokerListener brokerListener = new ParserListener(PARSER_SERVICE);
        new Thread(brokerListener).start();

        // start server
        Service.runService(null);
        brokerListener.terminate();
    }
    
}
