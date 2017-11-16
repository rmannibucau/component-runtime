/**
 *  Copyright (C) 2006-2017 Talend Inc. - www.talend.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
import React from 'react';
import { Toggle, Drawer } from '@talend/react-components';
import SchemaButton from '../Component/SchemaButton';
import Schema from '../Component/Schema';

export default class Mapper extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
    };
    this.drawerActions = {
      actions: {
        right: [
          {
            label: 'Close',
            bsStyle: 'primary',
            onClick: () => this.props.onUpdateDrawers([])
          }
        ]
      }
    };

    ['onStreamChange', 'onConfigurationButtonClick'].forEach(i => this[i] = this[i].bind(this));
  }

  onStreamChange() {
    this.props.component.source.stream = !this.props.component.source.stream;
    this.setState({});
  }

  onConfigurationButtonClick() {
    this.props.onUpdateDrawers([
      <Drawer title={`${this.props.component.configuration.name} Configuration Model`} footerActions={this.drawerActions}>
        <Schema schema={this.props.component.source.configurationStructure} onlyChildren={true} />
      </Drawer>
    ]);
  }

  render() {
    return (
      <mapper className={this.props.theme.Mapper}>
        <SchemaButton text="Configuration Model" onClick={this.onConfigurationButtonClick} />
        <div className={this.props.theme['form-row']}>
          <p className={this.props.theme.title}>Stream</p>
          <Toggle checked={this.props.component.source.stream} onChange={() => this.onStreamChange()} />
        </div>
      </mapper>
    );
  }
}
