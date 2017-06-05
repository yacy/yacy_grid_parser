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
        
        public boolean processAction(SusiAction action, JSONArray data) {
    
            String sourceasset_path = action.getStringAttr("sourceasset");
            String targetasset_path = action.getStringAttr("targetasset");
            String targetgraph_path = action.getStringAttr("targetgraph");
            boolean elastic = action.getBooleanAttr("bulk");
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
    }
    
    public static void main(String[] args) {
        BrokerListener brokerListener = new ParserListener();
        new Thread(brokerListener).start();
        List<Class<? extends Servlet>> services = new ArrayList<>();
        services.addAll(Arrays.asList(MCP.MCP_SERVICES));
        services.addAll(Arrays.asList(PARSER_SERVICES));
        Service.runService(SERVICE, DATA_PATH, APP_PATH, null, services);
    }
    
}
