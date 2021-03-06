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
import { Actions, WithDrawer } from '@talend/react-components';

import Input from '../Component/Input';
import Mapper from './Mapper';
import Processor from './Processor';

import theme from './Component.scss';

export default class Component extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      drawers: [],
      componentTypeActions: [
        {
          label: 'Input',
          className: theme.selected,
          _view: component => <Mapper component={component} theme={theme} onUpdateDrawers={this.updateDrawers} />
        },
        {
          label: 'Processor',
          _view: component => <Processor component={component} theme={theme}
                                         onUpdateDrawers={this.updateDrawers} />
        }
      ]
    };

    this.componentPerType = this.state.componentTypeActions.reduce((a, i) => {
      a[i.label] = i;
      return a;
    }, {});

    const onSelect = (ref, state, optProps) => {
      let props = optProps || this.props;
      props.component.type = ref.label;
      state.type = ref.label;
      state.drawers = [];
      state.componentTypeActions.forEach(i => {
        if (i.label !== ref.label) {
          delete i.className;
        } else {
          i.className = theme.selected;
        }
      });
    };
    // ensure the selected class is used when clicking on the component type buildToolActions
    this.state.componentTypeActions.forEach(item => {
      const ref = item;
      item.onClick = () => this.setState(state => onSelect(ref, state, this.props));
      item.init = props => this.setState(state => onSelect(ref, state, props));
    });

    this.updateDrawers = this.updateDrawers.bind(this);

    const selectedType = this.props.component.type || this.state.componentTypeActions[0].label;
    onSelect(this.state.componentTypeActions.filter(i => i.label === selectedType)[0], this.state);
  }

  updateDrawers(drawers) {
    this.setState({drawers});
  }

  componentWillReceiveProps(nextProps) {
    const selectedType = nextProps.component.type || this.state.componentTypeActions[0].label;
    this.state.componentTypeActions.filter(i => i.label === selectedType)[0].init(nextProps);
  }

  render() {
    const specificView = this.componentPerType[this.props.component.type]._view;
    return (
      <div className={theme.Component}>
        <WithDrawer drawers={this.state.drawers}>
          <div className={theme.main}>
            <div className={theme['form-row']}>
              <p className={theme.title}><em>{this.props.component.configuration.name || ''}</em> Configuration</p>
              <div>
                <Actions actions={this.state.componentTypeActions} />
              </div>
            </div>

            <div className={theme['form-row']}>
              <p className={theme.title}>Configuration</p>
              <form novalidate submit={e => e.preventDefault()}>
                <div className="field">
                  <label forHtml="componentName">Name</label>
                  <Input className="form-control" id="componentName" type="text" placeholder="Enter the component name..."
                         required="required" minLength="1" onChange={() => !!this.props.onChange && this.props.onChange()}
                         aggregate={this.props.component.configuration} accessor="name"/>
                </div>
              </form>
            </div>

            {specificView(this.props.component)}
          </div>
        </WithDrawer>
      </div>
    );
  }
}
