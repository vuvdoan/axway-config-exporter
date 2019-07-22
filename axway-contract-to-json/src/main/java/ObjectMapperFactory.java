import com.axway.apim.swagger.api.properties.APIDefintion;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonAppend;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

public class ObjectMapperFactory {

    public static ObjectMapper getObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        String[] propertiesToExclude = {"certFile", "useForInbound", "useForOutbound"};
        FilterProvider filters = new SimpleFilterProvider()
                .addFilter("IgnoreImportFields",
                        SimpleBeanPropertyFilter.serializeAllExcept(propertiesToExclude) );
        objectMapper.setFilterProvider(filters);


        // ignoring properties on classes from axway we can't change:
        objectMapper.addMixIn(APIDefintion.class, APIDefinitionSerialization.class);

        return objectMapper;
    }


    abstract class  APIDefinitionSerialization {
        @JsonIgnore
        abstract int getAPIDefinitionType();

        @JsonIgnore
        abstract public byte[] getAPIDefinitionContent();

    }
}
