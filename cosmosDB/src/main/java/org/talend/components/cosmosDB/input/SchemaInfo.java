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
package org.talend.components.cosmosDB.input;

import lombok.Data;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.meta.Documentation;

import java.io.Serializable;

@Data
public class SchemaInfo implements Serializable {

    @Option
    @Documentation("Studio type: Column")
    String label;

    @Option
    @Documentation("Studio type: Db Column")
    String originalDbColumnName;

    @Option
    @Documentation("Studio type: Key")
    Boolean key;

    @Option
    @Documentation("Studio type: DB Type")
    String type;

    @Option
    @Documentation("Studio type: Type")
    String talendType;

    @Option
    @Documentation("Studio type: Nullable")
    Boolean nullable;

    @Option
    @Documentation("Studio type: Date Pattern")
    String pattern;

    @Option
    @Documentation("Studio type: Length")
    int length;

    @Option
    @Documentation("Studio type: Precision")
    int precision;

    @Option
    @Documentation("Studio type: Default")
    String defaultValue;

    @Option
    @Documentation("Studio type: Comment")
    String comment;

    public String toPrettyString() {
        return " Column: " + getLabel() +
                " Db Column: " + getOriginalDbColumnName() +
                " Key: " + (getKey() ? "yes" : "no ") +
                " Type: " + getType() +
                " Talend Type: " + getTalendType() +
                " Null: " + (getNullable() ? "yes" : "no ") +
                " Date pattern: " + getPattern() +
                " Length: " + getLength() +
                " Precision: " + getPrecision() +
                " Default: " + getDefaultValue() +
                " Comment: " + getComment();
    }
}
