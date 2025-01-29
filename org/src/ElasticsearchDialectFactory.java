public class ElasticsearchDialectFactory {

    public ElasticsearchProtocolDialect createProtocolDialect(ElasticsearchVersion version) {
        int major = version.major();
        OptionalInt minorOptional = version.minor();
        if ( !minorOptional.isPresent() ) {
            // The version is supposed to be fetched from the cluster itself, so it should be complete
            throw new AssertionFailure(
                    "The Elasticsearch version is incomplete when creating the protocol dialect."
            );
        }
        int minor = minorOptional.getAsInt();

        if ( major < 5 ) {
            throw log.unsupportedElasticsearchVersion( version );
        }
        else if ( major == 5 ) {
            if ( minor < 6 ) {
                throw log.unsupportedElasticsearchVersion( version );
            }
            // Either the latest supported version, or a newer/unknown one
            if ( minor != 6 ) {
                log.unknownElasticsearchVersion( version );
            }
            return new Elasticsearch56ProtocolDialect();
        }
        else if ( major == 6 ) {
            if ( minor < 3 ) {
                return new Elasticsearch60ProtocolDialect();
            }
            if ( minor < 4 ) {
                return new Elasticsearch63ProtocolDialect();
            }
            if ( minor < 7 ) {
                return new Elasticsearch64ProtocolDialect();
            }
            // Either the latest supported version, or a newer/unknown one
            if ( minor > 8 ) {
                log.unknownElasticsearchVersion( version );
            }
            return new Elasticsearch67ProtocolDialect();
        }
        else {
            // Either the latest supported version, or a newer/unknown one
            if ( major != 7 ) {
                log.unknownElasticsearchVersion( version );
            }
            return new Elasticsearch70ProtocolDialect();
        }
    }
}
