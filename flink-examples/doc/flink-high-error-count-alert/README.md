# High Error Count Alert

The application reads apache access log from a Pravega stream and once every 2 seconds
counts the number of 500 responses in the last 30 seconds, and generates
alert when the counts of 5000 responses exceed 6.

The scripts can be found under the flink-examples directory in:
```
flink-examples/build/install/pravega-flink-examples/bin
```


## Prerequistes

1. Logstash installed (see [Install logstash](https://www.elastic.co/guide/en/logstash/5.6/installing-logstash.html)
2. Pravega running (see [here](http://pravega.io/docs/latest/getting-started/) for instructions)


Copy the contents under flink-examples/doc/flink-high-error-count-alert/filters/ to the box
where logstash is installed, e.g., in the directory of ~/pravega.
update **pravega_endpoint** in ~/pravega/90-pravega-output.conf.

```
output {
    pravega {
        pravega_endpoint => "tcp://10.247.134.71:9090"
        stream_name => "apacheaccess"
        scope => "myscope"
    }
}
```

Start logstash, assume it is installed at /usr/share/logstash/bin. The logstash log files are 
normally at /var/log/logstash.
```
/usr/share/logstash/bin -f ~/pravega
```

