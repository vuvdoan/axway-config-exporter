## How to build
```
mvn package 
```

## How to run
```
java -jar axway-config-exporter.jar -host <api-manager> -port 443 -u <your user> -p <your password> -a /mrpcomplex/weather/v1 -o weather-v1.json
```

whereas: 
- __host__: the API Manager host name
- __port__: the API Manager port
- __u__: the API Manager user name
- __p__: the API Manager user password
- __a__: the resource path of API, which should be exported as JSON. The resource path is considered as unique identifier
- __o__: the output name of the exported Axway API config file