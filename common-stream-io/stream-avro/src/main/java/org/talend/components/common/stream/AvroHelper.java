/*
 * Copyright (C) 2006-2022 Talend Inc. - www.talend.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.talend.components.common.stream;

import java.util.Optional;

import org.apache.avro.Schema;

import static org.talend.components.common.stream.Constants.AVRO_LOGICAL_TYPE;

public class AvroHelper {

    private AvroHelper() {
    }

    /**
     * Extract non nullable type from nullable.
     * (In Avro, nullable type is a union between Null type and 'Non nullable type',
     * This method allow to extract the non nullable type inside the union)
     * 
     * @param nullableType : avro type that can be a union with Null.
     * @return type inside union.
     */
    public static org.apache.avro.Schema nonNullableType(org.apache.avro.Schema nullableType) {
        final org.apache.avro.Schema elementType;
        if (nullableType.getType() == org.apache.avro.Schema.Type.UNION) {
            final Optional<Schema> extractedSchemas = nullableType
                    .getTypes()
                    .stream()
                    .filter(schema -> schema.getType() != org.apache.avro.Schema.Type.NULL)
                    .map((org.apache.avro.Schema sub) -> {
                        if (org.apache.avro.Schema.Type.UNION.equals(sub.getType())) {
                            return AvroHelper.nonNullableType(sub);
                        }
                        return sub;
                    })
                    .findFirst();
            // should have only one schema element with nullable (UNION)
            elementType = extractedSchemas.orElse(null);
        } else {
            elementType = nullableType;
        }
        return elementType;
    }

    public static org.apache.avro.Schema.Type getFieldType(org.apache.avro.Schema.Field field) {
        return nonNullableType(field.schema()).getType();
    }

    public static String getLogicalType(Schema.Field field) {
        return nonNullableType(field.schema()).getProp(AVRO_LOGICAL_TYPE);
    }
}
