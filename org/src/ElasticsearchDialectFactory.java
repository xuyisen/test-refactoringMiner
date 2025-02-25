/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.backend.elasticsearch.dialect.impl;

import java.lang.invoke.MethodHandles;
import java.util.OptionalInt;

import org.hibernate.search.backend.elasticsearch.ElasticsearchVersion;
import org.hibernate.search.backend.elasticsearch.dialect.model.impl.Elasticsearch56ModelDialect;
import org.hibernate.search.backend.elasticsearch.dialect.model.impl.Elasticsearch6ModelDialect;
import org.hibernate.search.backend.elasticsearch.dialect.model.impl.Elasticsearch7ModelDialect;
import org.hibernate.search.backend.elasticsearch.dialect.model.impl.ElasticsearchModelDialect;
import org.hibernate.search.backend.elasticsearch.dialect.protocol.impl.Elasticsearch56ProtocolDialect;
import org.hibernate.search.backend.elasticsearch.dialect.protocol.impl.Elasticsearch60ProtocolDialect;
import org.hibernate.search.backend.elasticsearch.dialect.protocol.impl.Elasticsearch63ProtocolDialect;
import org.hibernate.search.backend.elasticsearch.dialect.protocol.impl.Elasticsearch64ProtocolDialect;
import org.hibernate.search.backend.elasticsearch.dialect.protocol.impl.Elasticsearch67ProtocolDialect;
import org.hibernate.search.backend.elasticsearch.dialect.protocol.impl.Elasticsearch70ProtocolDialect;
import org.hibernate.search.backend.elasticsearch.dialect.protocol.impl.ElasticsearchProtocolDialect;
import org.hibernate.search.backend.elasticsearch.logging.impl.Log;
import org.hibernate.search.util.common.AssertionFailure;
import org.hibernate.search.util.common.logging.impl.LoggerFactory;

/**
 * Allows to create an Elasticsearch dialect by detecting the version of a remote cluster.
 */
public class ElasticsearchDialectFactory {

    private static final Log log = LoggerFactory.make( Log.class, MethodHandles.lookup() );

    public ElasticsearchModelDialect createModelDialect(ElasticsearchVersion version) {
        int major = version.major();
        OptionalInt minorOptional = version.minor();

        if ( major < 5 ) {
            throw log.unsupportedElasticsearchVersion( version );
        }
        else if ( major == 5 ) {
            if ( !minorOptional.isPresent() ) {
                throw log.ambiguousElasticsearchVersion( version );
            }
            int minor = minorOptional.getAsInt();
            if ( minor < 6 ) {
                throw log.unsupportedElasticsearchVersion( version );
            }
            return new Elasticsearch56ModelDialect();
        }
        else if ( major == 6 ) {
            return new Elasticsearch6ModelDialect();
        }
        else {
            return new Elasticsearch7ModelDialect();
        }
    }

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
            return createProtocolDialectForVersion5(minor, version);
        }
        else if ( major == 6 ) {
            return createProtocolDialectForVersion6(minor, version);
        }
        else {
            return createProtocolDialectForVersion7(version);
        }
    }

    private ElasticsearchProtocolDialect createProtocolDialectForVersion5(int minor, ElasticsearchVersion version) {
        if ( minor < 6 ) {
            throw log.unsupportedElasticsearchVersion( version );
        }
        // Either the latest supported version, or a newer/unknown one
        if ( minor != 6 ) {
            log.unknownElasticsearchVersion( version );
        }
        return new Elasticsearch56ProtocolDialect();
    }

    private ElasticsearchProtocolDialect createProtocolDialectForVersion6(int minor, ElasticsearchVersion version) {
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

    private ElasticsearchProtocolDialect createProtocolDialectForVersion7(ElasticsearchVersion version) {
        // Either the latest supported version, or a newer/unknown one
        if ( version.major() != 7 ) {
            log.unknownElasticsearchVersion( version );
        }
        return new Elasticsearch70ProtocolDialect();
    }
}