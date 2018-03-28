# High Error Count Alert

The application reads apache access log from a Pravega stream and once every 2 seconds
counts the number of 500 responses in the last 30 seconds, and generates
alert when the counts of 5000 responses exceed 6.

The scripts can be found under the flink-examples directory in:
```
flink-examples/build/install/pravega-flink-examples/bin
```


## Prerequistes ##

1. Logstash installed (see [Install logstash](https://www.elastic.co/guide/en/logstash/5.6/installing-logstash.html)
2. Pravega running (see [here](http://pravega.io/docs/latest/getting-started/) for instructions). The easiest is to download and run from the pre-built binaries.

## Run HighCountAlerter ##

```
$ cd flink-examples/build/install/pravega-flink-examples
$ bin/highCountAlerter [--controller tcp://127.0.0.1:9090] [--stream myscope/apacheaccess]
```

## Start Logstash with Pravega Output Plugin ##
Copy the contents under flink-examples/doc/flink-high-error-count-alert/filters/ to the host
where logstash is installed, e.g., in the directory of ~/pravega.
update **pravega_endpoint** in ~/pravega/90-pravega-output.conf.

```
output {
    pravega {
        pravega_endpoint => "tcp://127.0.0.1:9090"   <- update to point to your Pravega controller
        stream_name => "apacheaccess"
        scope => "myscope"
    }
}
```

Start logstash on the host where logstash is installed, assuming it is installed at /usr/share/logstash/bin.
It will take input from console and push the data to Pravega.
Note that sometimes it may take a minute or two for logstash to start. For troubleshooting, the logstash log files are 
normally at /var/log/logstash. To restart, type Ctrl-C, and re-run the command.

```
$ sudo /usr/share/logstash/bin -f ~/pravega
Sending Logstash's logs to /var/log/logstash which is now configured via log4j2.properties
The stdin plugin is now waiting for input:
```

## Generate Alert ##

In the logstash window, paste apache access logs like the followings:
```
10.1.1.11 - peter [19/Mar/2018:02:24:01 -0400] "GET /health/ HTTP/1.1" 200 182 "http://example.com/myapp" "Mozilla/5.0"
10.1.1.11 - peter [19/Mar/2018:02:24:01 -0400] "PUT /mapping/ HTTP/1.1" 500 182 "http://example.com/myapp" "python-client"
```
Logstash will push them to Pravega as well as print on the console, per the configuration in ~/pravega.
```
{
        "request" => "/health/",
          "agent" => "\"Mozilla/5.0\"",
           "auth" => "peter",
          "ident" => "-",
           "verb" => "GET",
        "message" => "10.1.1.11 - peter [19/Mar/2018:02:24:01 -0400] \"GET /health/ HTTP/1.1\" 200 182 \"http://example.com/myapp\" \"Mozilla/5.0\"",
       "referrer" => "\"http://example.com/myapp\"",
     "@timestamp" => 2018-03-19T06:24:01.000Z,
       "response" => "200",
          "bytes" => "182",
       "clientip" => "10.1.1.11",
       "@version" => "1",
           "host" => "lglca061.lss.emc.com",
    "httpversion" => "1.1"
}
{
        "request" => "/mapping/",
          "agent" => "\"python-client\"",
           "auth" => "peter",
          "ident" => "-",
           "verb" => "PUT",
        "message" => "10.1.1.11 - peter [19/Mar/2018:02:24:01 -0400] \"PUT /mapping/ HTTP/1.1\" 500 182 \"http://example.com/myapp\" \"python-client\"",
       "referrer" => "\"http://example.com/myapp\"",
     "@timestamp" => 2018-03-19T06:24:01.000Z,
       "response" => "500",
          "bytes" => "182",
       "clientip" => "10.1.1.11",
       "@version" => "1",
           "host" => "lglca061.lss.emc.com",
    "httpversion" => "1.1"
}
```

In the HighCountAlerter window, you should see output like the following. Once the 500 response counts is 6 or above, it
should print **High 500 responses** alerts.
```
3> Response count: 200 : 1
3> Response count: 500 : 1
3> Response count: 500 : 2
3> Response count: 200 : 2
3> Response count: 500 : 4
3> Response count: 200 : 4
3> Response count: 200 : 6
3> Response count: 500 : 6
2> High 500 responses: 500 : 6
3> Response count: 200 : 8
3> Response count: 500 : 8
3> High 500 responses: 500 : 8
3> Response count: 500 : 8
3> Response count: 200 : 8
2> High 500 responses: 500 : 8
3> Response count: 200 : 7
3> Response count: 500 : 7
3> High 500 responses: 500 : 7
3> Response count: 500 : 5
3> Response count: 200 : 5
3> Response count: 500 : 3
3> Response count: 200 : 3
3> Response count: 200 : 1
3> Response count: 500 : 1
```
