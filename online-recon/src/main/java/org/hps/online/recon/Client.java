package org.hps.online.recon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.hps.online.recon.commands.CommandFactory;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Client for interacting with the online reconstruction server.
 */
public final class Client {

    /**
     * Package logger.
     */
    private static Logger LOGGER = Logger.getLogger(Client.class.getPackageName());
    
    /**
     * Hostname of the server with default.
     */
    private String hostname = "localhost";
    
    /**
     * Port of the server with default.
     */
    private int port = 22222;

    /**
     * Output file for writing server responses.
     * By default it is null, which results in output being written to the console (System.out).
     */
    private File outputFile;
    
    /**
     * Parser for base options.
     */
    private CommandLineParser parser = new DefaultParser();
           
    /**
     * Append rather than overwrite if writing to output file.
     */
    private boolean append = false;
    
    private CommandFactory cf = new CommandFactory();
    
    /**
     * The base options (commands have their own Options objects).
     */
    private static Options OPTIONS = new Options();
    static {
        OPTIONS.addOption(new Option("", "help", false, "print help"));
        OPTIONS.addOption(new Option("", "help-all", false, "print help for all commands"));
        OPTIONS.addOption(new Option("p", "port", true, "server port"));
        OPTIONS.addOption(new Option("h", "host", true, "server hostname"));
        OPTIONS.addOption(new Option("o", "output", true, "output file (default writes server responses to System.out)"));
        OPTIONS.addOption(new Option("a", "append", false, "append if writing to output file (default will overwrite)"));
    }
   
    /**
     * Class constructor.
     */
    Client() { 
    }
    
    /**
     * Print the base command usage.
     */
    private void printUsage(boolean exit) {
        final HelpFormatter help = new HelpFormatter();
        final String commands = String.join(" ", cf.getCommands());
        help.printHelp("Client [options] [command] [command_options]", "Send commands to the online reconstruction server",
                OPTIONS, "Commands: " + commands + '\n'
                    + "Use 'Client [command] --help' for help with a specific command.");
        if (exit) {
            System.exit(0);
        }
    }
    
    private void printCommandUsages() {
        for (String cmd : cf.getCommands()) {
            cf.create(cmd).printUsage();
            System.out.println();
        }
    }
               
    /**
     * Run the client using command line arguments
     * @param args The command line arguments
     */
    void run(String args[]) {
        
        // Parse base options.
        CommandLine cl;
        try {
            cl = this.parser.parse(OPTIONS, args, true);
        } catch (ParseException e) {
            throw new RuntimeException("Error parsing arguments", e);
        }

        // Print usage and exit.
        if (cl.hasOption("help")) {
            this.printUsage(true);
        }

        // Print usage for all commands and exit.
        if (cl.hasOption("help-all")) {
            System.out.println("CLIENT" + '\n');
            this.printUsage(false);    
            System.out.println('\n' + "COMMANDS");
            this.printCommandUsages();
            System.exit(0);
        }

        // Get extra arg list.
        List<String> argList = cl.getArgList();

        if (cl.hasOption("p")) {
            this.port = Integer.parseInt(cl.getOptionValue("p"));
            LOGGER.config("Port: " + this.port);
        }

        if (cl.hasOption("h")) {
            this.hostname = cl.getOptionValue("h");
            LOGGER.config("Hostname: " + this.hostname);
        }

        if (cl.hasOption("o")) {
            this.outputFile = new File(cl.getOptionValue("o"));
            LOGGER.config("Output file: " + this.outputFile.getPath());
        }
        
        if (cl.hasOption("a")) {
            this.append = true;
            LOGGER.config("Appending to output file: " + this.append);
        }

        // If extra arguments are provided then try to run a command.
        if (argList.size() != 0) {
            String commandName = argList.get(0);
            Command command = cf.create(commandName);
            if (command == null) {
                printUsage(false);
                throw new IllegalArgumentException("Unknown command: " + commandName);
            }

            // Remove command from arg list.
            argList.remove(0);

            // Convert command list to array.
            String[] argArr = argList.toArray(new String[0]);

            // Parse command options.
            DefaultParser commandParser = new DefaultParser();
            CommandLine cmdResult = null;
            try {
                cmdResult = commandParser.parse(command.getOptions(), argArr);
            } catch (ParseException e) {
                command.printUsage();
                throw new RuntimeException("Error parsing command options", e);            
            }

            // Setup the command parameters from the parsed options.
            command.process(cmdResult);

            // Send the command to server.
            LOGGER.info("Sending command " + command.toString());
            send(command);
        } else {
            // No command was provided so run the interactive console.
            Console cn = new Console(this);
            cn.run();
        }
    }
    
    /**
     * Send a command to the online reconstruction server.
     * @param command The client command to send
     */
    void send(Command command) {
        try (Socket socket = new Socket(hostname, port)) {
            // Send command to the server.           
            PrintWriter writer = new PrintWriter(socket.getOutputStream());
            writer.write(command.toString() + '\n');            
            writer.flush();

            // Get server response.
            InputStream is = socket.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String resp = br.readLine();
            
            // Print server response or write to output file.
            PrintWriter pw = null;
            if (this.outputFile != null) {
                FileWriter fw = new FileWriter(this.outputFile, this.append);
                pw = new PrintWriter(fw);
            }
            if (resp.startsWith("{")) {
                // Handle JSON object.
                JSONObject jo = new JSONObject(resp);
                if (pw != null) {
                    pw.write(jo.toString(4) + '\n');
                } else {
                    System.out.println(jo.toString(4));
                }
            } else if (resp.startsWith("[")) {
                // Handle JSON array.
                JSONArray ja = new JSONArray(resp);
                if (pw != null) {
                    pw.write(ja.toString(4) + '\n');
                } else {
                    System.out.println(ja.toString(4));
                }
            } else {
                // Response from server isn't valid JSON.
                throw new RuntimeException("Invalid server response: " + resp.toString());
            }
            if (pw != null) {
                LOGGER.info("Wrote server response to: " + this.outputFile.getPath());
                pw.flush();
                pw.close();
            }
        } catch (Exception e) {
            throw new RuntimeException("Client error", e);
        }
    }
    
    /**
     * Run the client from the command line.
     * @param args The argument array
     */
    public static void main(String[] args) {
        Client client = new Client();
        client.run(args);
    }
    
    String getHostname() {
        return this.hostname;
    }
    
    int getPort() {
        return this.port;
    }
    
    File getOutputFile() {
        return this.outputFile;
    }
        
    void setPort(int port) {
        this.port = port;
    }
    
    void setHostname(String hostname) {
        this.hostname = hostname;
    }
    
    void setOutputFile(String outputPath) {
        if (outputPath != null) {
            this.outputFile = new File(outputPath);
        } else {
            this.outputFile = null;
        }
    }        
    
    void setAppend(boolean append) {
        this.append = append;
    }
    
    boolean getAppend() {
        return this.append;
    }
}
