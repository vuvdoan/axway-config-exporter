import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

import com.axway.apim.lib.AppException;
import com.axway.apim.lib.CommandParameters;
import com.axway.apim.lib.EnvironmentProperties;
import com.axway.apim.lib.RelaxedParser;
import com.axway.apim.swagger.api.state.IAPI;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

public class Main {

    public static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws AppException, IOException, ParseException {
        String host;
        String port;
        String username;
        String password;
        String apiId;
        String output;

        try {
            Options options = new Options();
            Option option;

            option = new Option("a", "apiId", true, "The app identifier in Axway. For now this is the apiPath");
            option.setRequired(true);
            options.addOption(option);

            option = new Option("h", "host", true, "The API-Manager hostname the API should be imported");
            option.setRequired(false);
            option.setArgName("api-host");
            options.addOption(option);

            option = new Option("port", true, "Optional parameter to declare the API-Manager port. Defaults to 8075.");
            option.setArgName("8181");
            options.addOption(option);

            option = new Option("u", "username", true,
                    "Username used to authenticate. Please note, that this user must have Admin-Role");
            option.setRequired(false);
            option.setArgName("apiadmin");
            options.addOption(option);

            option = new Option("p", "password", true, "Password used to authenticate");
            option.setRequired(false);
            option.setArgName("changeme");
            options.addOption(option);

            option = new Option("o", "output", true, "Output file");
            option.setRequired(false);
            option.setArgName("changeme");
            options.addOption(option);

            CommandLineParser parser = new RelaxedParser();

            CommandLine cmd = null;

            cmd = parser.parse(options, args);
            host = cmd.getOptionValue("host");
            port = cmd.getOptionValue("port");
            username = cmd.getOptionValue("username");
            password = cmd.getOptionValue("password");
            apiId = cmd.getOptionValue("apiId");
            output = cmd.getOptionValue("output");
            try {
                String[] argValues = { apiId, host, port, username, password };
                LOG.info("Login to " + host + ":" + port);
                final CommandLine internalCmd = parser.parse(new Options(), argValues);
                CommandParameters params = new CommandParameters(cmd, internalCmd,
                        new EnvironmentProperties(cmd.getOptionValue("stage"))); //NOSONAR

                exportApi(apiId, output);
            } catch (ParseException e) {
                LOG.error(e);
                System.exit(99);
            }

        } catch (Exception e) {
            LOG.error(e);
            System.exit(-1);
        }
    }

    private static void exportApi(final String apiPath, String output) throws AppException, IOException {
        if (output == null || output.isEmpty()) {
            output = "exported.json";
        }
        LOG.info("Exporting " + apiPath);
        final APIManager apiManager = new APIManager();
        final IAPI actualAPI = apiManager.getApi(apiPath);
        ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();
        objectMapper.writeValue(new File(output), actualAPI);
    }

}
