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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.servlet.Servlet;

import net.yacy.document.LibraryProvider;
import net.yacy.grid.YaCyServices;
import net.yacy.grid.mcp.BrokerListener;
import net.yacy.grid.mcp.Configuration;
import net.yacy.grid.mcp.MCP;
import net.yacy.grid.mcp.Service;
import net.yacy.grid.parser.api.JSONLDValidatorService;
import net.yacy.grid.parser.api.ParserService;
import net.yacy.grid.tools.CronBox;
import net.yacy.grid.tools.CronBox.Telemetry;
import net.yacy.grid.tools.GitTool;
import net.yacy.grid.tools.Logger;

public class Parser {

    private final static YaCyServices PARSER_SERVICE = YaCyServices.parser;
    private final static String DATA_PATH = "data";
    private final static String LIBRARY_PATH = "conf/libraries/";

    // define services
    @SuppressWarnings("unchecked")
    public final static Class<? extends Servlet>[] PARSER_SERVICES = new Class[]{
            // information services
            ParserService.class,
            JSONLDValidatorService.class
    };

    public static class Application implements CronBox.Application {

        final Configuration config;
        final Service service;
        final BrokerListener brokerApplication;
        final CronBox.Application serviceApplication;

        public Application() {
            Logger.info("Starting Parser Application...");
            Logger.info(new GitTool().toString());

            // initialize configuration
            final List<Class<? extends Servlet>> services = new ArrayList<>();
            services.addAll(Arrays.asList(MCP.MCP_SERVLETS));
            services.addAll(Arrays.asList(PARSER_SERVICES));
            this.config =  new Configuration(DATA_PATH, true, PARSER_SERVICE, services.toArray(new Class[services.size()]));

            // initialize REST server with services
            this.service = new Service(this.config);

            // connect backend
            this.config.connectBackend();

            // initiate broker application: listening to indexing requests at RabbitMQ
            this.brokerApplication = new ParserListener(this.config, PARSER_SERVICE);

            // initiate service application: listening to REST request
            this.serviceApplication = this.service.newServer(null);
        }

        @Override
        public void run() {

            Logger.info("Grid Name: " + this.config.properties.get("grid.name"));

            // starting threads
            new Thread(this.brokerApplication).start();
            this.serviceApplication.run(); // SIC! the service application is running as the core element of this run() process. If we run it concurrently, this runnable will be "dead".
        }

        @Override
        public void stop() {
            Logger.info("Stopping Parser Application...");
            this.serviceApplication.stop();
            this.brokerApplication.stop();
            this.service.stop();
            this.service.close();
            this.config.close();
        }

        @Override
        public Telemetry getTelemetry() {
            return null;
        }

    }

    public static void main(final String[] args) {
        // run in headless mode
        System.setProperty("java.awt.headless", "true"); // no awt used here so we can switch off that stuff

        // XML parser configuration
        System.getProperties().put("jdk.xml.totalEntitySizeLimit", "0");
        System.getProperties().put("jdk.xml.entityExpansionLimit", "0");

        // Initialize Libraries
        new Thread("LibraryProvider.initialize") {
            @Override
            public void run() {
                LibraryProvider.initialize(new File(LIBRARY_PATH));
            }
        }.start();

        // prepare configuration
        final Properties sysprops = System.getProperties(); // system properties
        System.getenv().forEach((k,v) -> {
            if (k.startsWith("YACYGRID_")) sysprops.put(k.substring(9).replace('_', '.'), v);
        }); // add also environment variables

        // first greeting
        Logger.info("Parser started!");
        Logger.info(new GitTool().toString());

        // run application with cron
        final long cycleDelay = Long.parseLong(System.getProperty("YACYGRID_PARSER_CYCLEDELAY", "" + Long.MAX_VALUE)); // by default, run only in one genesis thread
        final int cycleRandom = Integer.parseInt(System.getProperty("YACYGRID_PARSER_CYCLERANDOM", "" + 1000 * 60 /*1 minute*/));
        final CronBox cron = new CronBox(Application.class, cycleDelay, cycleRandom);
        cron.cycle();

        // this line is reached if the cron process was shut down
        Logger.info("Parser terminated");
    }

}
