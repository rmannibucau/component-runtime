/**
 *  Copyright (C) 2006-2018 Talend Inc. - www.talend.com
 *
 *  Licensed under the Apache License, Version 2.0 (the 'License');
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an 'AS IS' BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import { IconsProvider, HeaderBar, Layout, Notification, CircularProgress } from '@talend/react-components';

import Menu from '../Menu';
import Detail from '../Detail';

import theme from './App.scss';

export default class App extends React.Component {
  constructor(props) {
    super(props);
    this.notificationLeaveFn = this.notificationLeaveFn.bind(this);
    this.onHelp = this.onHelp.bind(this);
  }

  notificationLeaveFn(notification) {
    this.props.removeNotification(notification);
  }

  render() {
    const header = (
      <HeaderBar
        logo={{ isFull: true }}
        brand={{
          id: 'header-brand',
          label: 'Talend Component Kit Web Tester'
        }}
      />
    );
    const menu = (<Menu />);

    return (
      <div className={theme.App}>
        <IconsProvider/>
        <Layout mode={'TwoColumns'} header={header} one={menu}>
          <Detail />
        </Layout>
        <Notification notifications={this.props.notifications} leaveFn={this.notificationLeaveFn} />
      </div>
    );
  }
}
