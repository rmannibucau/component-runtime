/**
 *  Copyright (C) 2006-2019 Talend Inc. - www.talend.com
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
import PropTypes from 'prop-types';
import Icon from '@talend/react-components/lib/Icon';
import { Action, Actions } from '@talend/react-components/lib/Actions';

import Help from '../Help';
import FacetSelector from '../FacetSelector';
import CategorySelector from '../CategorySelector';
import Input from '../Input';
import ProjectContext from '../../ProjectContext';

import theme from './ProjectMetadata.scss';

/* eslint-disable no-param-reassign */

export default class ProjectMetadata extends React.Component {
	static propTypes = {
		project: PropTypes.object,
	};
	constructor(props) {
		super(props);
		this.state = {
			project: props.project,
			buildToolActions: [],
			facets: {},
			showAll: false,
		};
		this.showAll = this.showAll.bind(this);
		this.showLight = this.showLight.bind(this);
	}

	onCategoryUpdate(value) {
		this.setState(current => (current.project.category = value.value));
	}

	showAll(event) {
		this.setState({ showAll: true });
		event.preventDefault();
	}

	showLight(event) {
		this.setState({ showAll: false });
		event.preventDefault();
	}

	render() {
		return (
			<ProjectContext.Consumer>
				{project => (
					<div className={theme.ProjectMetadata}>
						<div className={theme.main}>
							<div className={theme['form-row']}>
								<h1>Create a Talend Component Family Project</h1>
								<div>
									<Actions
										actions={project.configuration.buildTypes.map(label => ({
											label,
											bsStyle: project.project.buildType === label ? 'info' : 'default',
											className: project.project.buildType !== label ? 'btn-inverse' : '',
											onClick: () => {
												project.selectBuildTool(label);
											},
										}))}
									/>
									<Help
										title="Build Tool"
										i18nKey="project_build_tool"
										content={(
											<div>
												<p>
													Maven is the most commonly used build tool and Talend Component Kit
													integrates with it smoothly.
												</p>
												<p>
													Gradle is less used but get more and more attraction because it is
													communicated as being faster than Maven.
												</p>
												<p>
													<Icon name="talend-warning" /> Talend Component Kit does not provide as
													much features with Gradle than with Maven. The components validation is
													not yet supported for instance.
												</p>
											</div>
										)}
									/>
								</div>
							</div>

							<div className={theme['form-row']}>
								{!!project.configuration && (
									<FacetSelector
										facets={project.configuration.facets}
										selected={project.project.facets}
									/>
								)}
							</div>

							<div className={theme['form-row']}>
								<h2>Component Metadata</h2>
								<form noValidate onSubmit={e => e.preventDefault()}>
									<div className="field">
										<label htmlFor="projectFamily">Component Family</label>
										<Help
											title="Family"
											i18nKey="project_family"
											content={
												<span>
													<p>The family groups multiple components altogether.</p>
													<p>
														<Icon name="talend-info-circle" /> It is recommended to use a single
														family name per component module. The name must be a valid java name (no
														space, special characters, ...).
													</p>
												</span>
											}
										/>
										<Input
											className="form-control"
											id="projectFamily"
											type="text"
											placeholder="Enter the component family..."
											required
											aggregate={project.project}
											accessor="family"
										/>
									</div>
									<div className="field">
										<label htmlFor="projectCategory">Category</label>
										<Help
											title="Category"
											i18nKey="project_category"
											content={
												<span>
													<p>
														The category is a group used by the Studio to organize components of
														different families in the same bucket into the <code>Palette</code>.
													</p>
													<p>
														It is recommended to use a two level category. The first level is
														generally very general and the second one is close to the family name.
													</p>
													<Icon name="talend-info-circle" /> The names must be valid java names (no
													space, special characters, ...).
												</span>
											}
										/>
										<CategorySelector
											initialValue={project.project.category}
											onChange={value => this.onCategoryUpdate(value)}
										/>
									</div>
								</form>
							</div>

							<h2>Project Metadata</h2>
							<form noValidate onSubmit={e => e.preventDefault()}>
								<div className="field">
									<label htmlFor="projectGroup">Group</label>
									<Help
										title="Project Group"
										i18nKey="project_group"
										content={
											<span>
												<p>
													The project group used when deployed on a repository (like a Nexus or
													central).
												</p>
												<p>
													The best practice recommends to use the reversed company hostname suffixed
													with something specific to the project.
												</p>
												<p>
													Example: <code>company.com</code> would lead to <code>com.company</code>{' '}
													package and for a component the used package would be, for instance,{' '}
													<code>com.company.talend.component</code>.
												</p>
											</span>
										}
									/>
									<Input
										className="form-control"
										id="projectGroup"
										type="text"
										placeholder="Enter the project group..."
										required
										aggregate={this.state.project}
										accessor="group"
									/>
								</div>
								<div className="field">
									<label htmlFor="projectArtifact">Artifact</label>
									<Help
										title="Project Artifact"
										i18nKey="project_artifact"
										content={
											<span>
												<p>
													The project artifact used when deployed on a repository (like a Nexus or
													central).
												</p>
												<p>It must be a unique identifier in the group namespace.</p>
												<p>
													Talend recommendation is to follow the pattern{' '}
													<code>
														${'{'}component{'}'}-component
													</code>{' '}
													but you can use whatever you want.
												</p>
											</span>
										}
									/>
									<Input
										className="form-control"
										id="projectArtifact"
										type="text"
										placeholder="Enter the project artifact..."
										required
										aggregate={this.state.project}
										accessor="artifact"
									/>
								</div>
								<div className="field">
									<label htmlFor="projectPackage">Package</label>
									<Help
										title="Project Root package"
										i18nKey="project_package"
										content={
											<span>
												<p>The root package represents a unique namespace in term of code.</p>
												<p>Talend recommendation is to align it on the selected group.</p>
											</span>
										}
									/>
									<Input
										className="form-control"
										id="projectPackage"
										type="text"
										placeholder="Enter the project base package..."
										required
										aggregate={this.state.project}
										accessor="packageBase"
									/>
								</div>

								{!this.state.showAll && (
									<Action
										bsStyle="link"
										onClick={this.showAll}
										label="see more options"
										className="btn-xs"
										icon="talend-plus-circle"
									/>
								)}

								{this.state.showAll && (
									<React.Fragment>
										<div className="field">
											<label htmlFor="projectVersion">Version</label>
											<Help
												title="Project Version"
												i18nKey="project_version"
												content={
													<span>
														<p>The version to use when deploying the artifact.</p>
														<p>
															Generally this generator is used for a first version so the default
															should fit without modification.
														</p>
													</span>
												}
											/>
											<Input
												className="form-control"
												id="projectVersion"
												type="text"
												placeholder="Enter the project version..."
												aggregate={this.state.project}
												accessor="version"
											/>
										</div>
										<div className="field">
											<label htmlFor="projectName">Project Name</label>
											<Help
												title="Project Name"
												i18nKey="project_name"
												content={
													<span>
														<p>
															Giving a human readable name to the project is more friendly in an IDE
															or continuous integration platform.
														</p>
													</span>
												}
											/>
											<Input
												className="form-control"
												id="projectName"
												type="text"
												placeholder="Enter the project name..."
												aggregate={this.state.project}
												accessor="name"
											/>
										</div>
										<div className="field">
											<label htmlFor="projectDescription">Project Description</label>
											<Help
												title="Project Description"
												i18nKey="project_description"
												content={
													<span>
														<p>
															Giving a human readable description to the project allows to share
															some goals of the project with other developers in a standard fashion.
														</p>
													</span>
												}
											/>
											<Input
												className="form-control"
												id="projectDescription"
												type="text"
												placeholder="Enter the project description..."
												aggregate={this.state.project}
												accessor="description"
											/>
										</div>
										<Action
											icon="talend-zoomout"
											label="See less options"
											bsStyle="link"
											className="btn-xs"
											onClick={this.showLight}
										/>
									</React.Fragment>
								)}
							</form>
						</div>
					</div>
				)}
			</ProjectContext.Consumer>
		);
	}
}
