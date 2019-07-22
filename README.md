## How to build
```
mvn package 
```

## How to run
```
java -jar axway-config-exporter.jar -host int-api-manager.bmwgroup.net -port 443 -u <your user> -p <your password> -a /mrpcomplex/weather/v1 -o weather-v1.json
```

whereas: 
- __host__: the API Manager host name
- port: the API Manager port
- u: the API Manager user name
- p: the API Manager user password
- a: the resource path of API, which should be exported as JSON. The resource path is considered as unique identifier
- o: the output name of the exported Axway API config file