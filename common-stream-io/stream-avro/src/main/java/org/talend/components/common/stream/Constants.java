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

public class Constants {

    private Constants() {
        throw new IllegalStateException("This is a utility class and cannot be instantiated");
    }

    public static final String AVRO_LOGICAL_TYPE = "logicalType";

    public static final String AVRO_LOGICAL_TYPE_DATE = "date";

    public static final String AVRO_LOGICAL_TYPE_TIME_MILLIS = "time-millis";

    public static final String AVRO_LOGICAL_TYPE_TIMESTAMP_MILLIS = "timestamp-millis";

    public static final String AVRO_LOGICAL_TYPE_DECIMAL = "decimal";

    public static final String ERROR_UNDEFINED_TYPE = "Undefined type %s.";

    public static final String STUDIO_LENGTH = "talend.studio.length";

    public static final String STUDIO_PRECISION = "talend.studio.precision";

    public static final String BIGDECIMAL = "id_BigDecimal";

    public static final String STUDIO_TYPE = "talend.studio.type";
}
