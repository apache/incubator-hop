/*! ******************************************************************************
 *
 * Hop : The Hop Orchestration Platform
 *
 * Copyright (C) 2002-2017 by Hitachi Vantara : http://www.pentaho.com
 * http://www.project-hop.org
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.apache.hop.pipeline.transforms.pgpdecryptstream;

import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.workflow.actions.pgpencryptfiles.GPG;
import org.apache.hop.pipeline.transform.BaseTransformData;
import org.apache.hop.pipeline.transform.ITransformData;

/**
 * @author Samatar
 * @since 03-Juin-2008
 */
public class PGPDecryptStreamData extends BaseTransformData implements ITransformData {
  public int indexOfField;
  public IRowMeta previousRowMeta;
  public IRowMeta outputRowMeta;
  public int NrPrevFields;

  public GPG gpg;
  public String passPhrase;
  public int indexOfPassphraseField;

  public PGPDecryptStreamData() {
    super();
    this.indexOfField = -1;
    this.gpg = null;
    this.passPhrase = null;
    this.indexOfPassphraseField = -1;
  }

}
