/*
 * Copyright (C) 2006-2021 Talend Inc. - www.talend.com
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
package org.talend.components.adlsgen2.output;

import java.io.Serializable;

import org.talend.components.adlsgen2.dataset.AdlsGen2DataSet;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayout;
import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.api.configuration.condition.ActiveIf;

import lombok.Data;

@Data
@GridLayout(value = { //
        @GridLayout.Row({ "dataSet" }), //
})
@GridLayout(names = GridLayout.FormType.ADVANCED, value = { //
        @GridLayout.Row({ "dataSet" }), //
        @GridLayout.Row({ "blobNameTemplate" }), //
        @GridLayout.Row({ "fileExtensionOverride" }), //
        @GridLayout.Row({ "fileExtension" }), // 
        @GridLayout.Row({ "fileFormatNoUUID" }), //
        @GridLayout.Row({ "fileExistsException" }) })
@Documentation("ADLS output configuration")
public class OutputConfiguration implements Serializable {

    @Option
    @Documentation("Dataset")
    private AdlsGen2DataSet dataSet;

    @Option
    @Documentation("Generated blob item name prefix.\nBatch file would have name prefix + UUID + extension.\n"
            + "I.e. myPrefix-5deaa8ff-7d22-4b86-a864-9a6fa414501a.avro")
    private String blobNameTemplate = "data-";

    @Option
    @Documentation("Override File extension")
    private boolean fileExtensionOverride;

    @Option
    @ActiveIf(target = "fileExtensionOverride", value = "true")
    @Documentation("File extension to use")
    private String fileExtension;

    @Option
    @Documentation("Don't generate UUID to output file")
    private boolean fileFormatNoUUID;

    //What happens if we already have the file?
    @Option
    @Documentation("Throw an error if the file already exist")
    @ActiveIf(target = "fileFormatNoUUID", value = "true")
    private boolean fileExistsException;

}
