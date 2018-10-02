/**
 *  Copyright (C) 2006-2018 Talend Inc. - www.talend.com
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
import { Action } from '@talend/react-components';
import { Inject, cmfConnect } from '@talend/react-cmf';
import errors from '@talend/react-forms/lib/UIForm/utils/errors';
import kit from '@talend/react-containers/lib/ComponentForm/kit';
import service from '@talend/react-containers/lib/ComponentForm/kit/defaultRegistry';

import theme from './Detail.scss';

function NoSelectedComponent() {
	return (
		<div>
			<h1>No component selected</h1>
			<p>Click on a component to see its form</p>
		</div>
	);
}

function parseQuery() {
    const queryString = window.location.search || '?';
    let query = {
        language: 'en'
    };
    return (queryString.length && queryString[0] === '?' ? queryString.substr(1) : queryString)
        .split('&')
        .map(pair => pairs[i].split('='))
        .reduce((a, p) => a[decodeURIComponent(pair[0])] = decodeURIComponent(pair[1] || ''), {});
}

function Detail(props) {
    const componentId = (props.componentId || {});
    const helpToggle = !componentId.id || !componentId.family || props.configurationSelected ? undefined : (
        <div className="col-md-12">
            <Action
              label={props.help ? 'Hide Help' : 'Show Help'}
              icon="talend-question-circle"
              onClick={() => props.toggleHelp({ toggled: !props.help, id: componentId.id })}
            />
        </div>
    );
    if (props.help) {
        return (
            <div className={theme.Detail}>
                <div className={`col-md-6 ${theme.Help}`} dangerouslySetInnerHTML={{__html: props.help}} />
                <div className="col-md-6">{helpToggle}</div>
            </div>
        );
    }

    let notSelected = null;
    let submitted = null;
    let form = null;

    const validationWithSuccessFeedback = ({ trigger, schema, body, errors }) => {
        if (body.status === 'OK' && trigger.type === 'healthcheck') {
            props.onNotification({
                id: `healthcheck_${new Date().getTime()}`,
                title: 'Success',
                message: body.comment || `Trigger ${trigger.type} / ${trigger.family} / ${trigger.action} succeeded`,
            });
        }
        return service.validation({ schema, body, errors });
    };
    const registry = {
        healthcheck: validationWithSuccessFeedback,
    };

    if (!props.definitionURL) {
        notSelected = (<NoSelectedComponent/>);
    }  else {
        form = (
            <Inject
                componentId="detail-form"
                component="ComponentForm"
                definitionURL={`/api/v1${props.definitionURL}`}
                triggerURL="/api/v1/application/action"
                customTriggers={registry}
            />
        );
        if (props.submitted) {
            const configuration = kit.flatten(props.uiSpec.properties);
            submitted = (
                <div>
                    <pre>{JSON.stringify(configuration, undefined, 2)}</pre>
                </div>
            );
        }
    }
    return (
        <div>
            <div className="col-md-6">
                {notSelected}
                {form}
            </div>
            <div className="col-md-6">
                {helpToggle}
                {submitted}
            </div>
        </div>
    );
}

export default Detail;
