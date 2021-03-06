package com.x.processplatform.assemble.surface.jaxrs.form;

import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;

import com.x.base.core.container.EntityManagerContainer;
import com.x.base.core.container.factory.EntityManagerContainerFactory;
import com.x.base.core.project.exception.ExceptionAccessDenied;
import com.x.base.core.project.exception.ExceptionEntityNotExist;
import com.x.base.core.project.http.ActionResult;
import com.x.base.core.project.http.EffectivePerson;
import com.x.base.core.project.logger.Logger;
import com.x.base.core.project.logger.LoggerFactory;
import com.x.base.core.project.tools.PropertyTools;
import com.x.processplatform.assemble.surface.Business;
import com.x.processplatform.assemble.surface.jaxrs.form.ActionGetWithWorkOrWorkCompletedMobile.Wo;
import com.x.processplatform.assemble.surface.jaxrs.form.BaseAction.AbstractWo;
import com.x.processplatform.core.entity.content.Work;
import com.x.processplatform.core.entity.content.WorkCompleted;
import com.x.processplatform.core.entity.content.WorkCompletedProperties;
import com.x.processplatform.core.entity.element.Activity;
import com.x.processplatform.core.entity.element.Form;
import com.x.processplatform.core.entity.element.Script;

class V2LookupWorkOrWorkCompletedMobile extends BaseAction {

	private static Logger logger = LoggerFactory.getLogger(V2LookupWorkOrWorkCompletedMobile.class);

	ActionResult<Wo> execute(EffectivePerson effectivePerson, String workOrWorkCompleted) throws Exception {
		try (EntityManagerContainer emc = EntityManagerContainerFactory.instance().create()) {
			ActionResult<Wo> result = new ActionResult<>();
			Business business = new Business(emc);
			if (!business.readableWithWorkOrWorkCompleted(effectivePerson, workOrWorkCompleted,
					new ExceptionEntityNotExist(workOrWorkCompleted))) {
				throw new ExceptionAccessDenied(effectivePerson);
			}
			Wo wo = new Wo();
			Work work = emc.find(workOrWorkCompleted, Work.class);
			if (null != work) {
				this.work(business, work, wo);
			} else {
				this.workCompleted(business, emc.flag(workOrWorkCompleted, WorkCompleted.class), wo);
			}
			result.setData(wo);
			return result;
		}
	}

	private void work(Business business, Work work, Wo wo) throws Exception {
		String id = work.getForm();
		if (!StringUtils.isEmpty(id)) {
			wo.setId(id);
		} else {
			Activity activity = business.getActivity(work);
			id = PropertyTools.getOrElse(activity, Activity.form_FIELDNAME, String.class, "");
		}
		wo.setId(id);
	}

	private void workCompleted(Business business, WorkCompleted workCompleted, Wo wo) throws Exception {
		// 先使用当前库的表单,如果不存在使用储存的表单.
		if (StringUtils.isNotEmpty(workCompleted.getForm())) {
			Form form = business.form().pick(workCompleted.getForm());
			if (null != form) {
				wo.setForm(toWoFormDataOrMobileData(form));
				related(business, wo, form);
			}
		} else if (null != workCompleted.getProperties().getForm()) {
			wo.setForm(toWoFormDataOrMobileData(workCompleted.getProperties().getForm()));
			if (StringUtils.isNotBlank(workCompleted.getProperties().getForm().getData())) {
				workCompleted.getProperties().getRelatedFormList()
						.forEach(o -> wo.getRelatedFormMap().put(o.getId(), toWoFormDataOrMobileData(o)));
			} else {
				workCompleted.getProperties().getMobileRelatedFormList()
						.forEach(o -> wo.getRelatedFormMap().put(o.getId(), toWoFormMobileDataOrData(o)));
			}
		}
		workCompleted.getProperties().getRelatedScriptList().stream()
				.forEach(o -> wo.getRelatedScriptMap().put(o.getId(), toWoScript(o)));
	}

	private void related(Business business, Wo wo, Form form) throws Exception {
		if (StringUtils.isNotBlank(form.getMobileData())) {
			for (String mobileRelatedFormId : form.getProperties().getMobileRelatedFormList()) {
				Form relatedForm = business.form().pick(mobileRelatedFormId);
				if (null != relatedForm) {
					wo.getRelatedFormMap().put(mobileRelatedFormId, toWoFormMobileDataOrData(relatedForm));
				}
			}
		} else {
			for (String relatedFormId : form.getProperties().getRelatedFormList()) {
				Form relatedForm = business.form().pick(relatedFormId);
				if (null != relatedForm) {
					wo.getRelatedFormMap().put(relatedFormId, toWoFormDataOrMobileData(relatedForm));
				}
			}
		}
		relatedScript(business, wo, form);
	}

	protected void relatedScript(Business business, AbstractWo wo, Form form) throws Exception {
		for (Entry<String, String> entry : form.getProperties().getMobileRelatedScriptMap().entrySet()) {
			switch (entry.getValue()) {
			case WorkCompletedProperties.Script.TYPE_PROCESSPLATFORM:
				Script relatedScript = business.script().pick(entry.getKey());
				if (null != relatedScript) {
					wo.getRelatedScriptMap().put(entry.getKey(), toWoScript(relatedScript));
				}
				break;
			case WorkCompletedProperties.Script.TYPE_CMS:
				com.x.cms.core.entity.element.Script relatedCmsScript = business.cms().script().pick(entry.getKey());
				if (null != relatedCmsScript) {
					wo.getRelatedScriptMap().put(entry.getKey(), toWoScript(relatedCmsScript));
				}
				break;
			case WorkCompletedProperties.Script.TYPE_PORTAL:
				com.x.portal.core.entity.Script relatedPortalScript = business.portal().script().pick(entry.getKey());
				if (null != relatedPortalScript) {
					wo.getRelatedScriptMap().put(entry.getKey(), toWoScript(relatedPortalScript));
				}
				break;
			default:
				break;
			}
		}
	}

	public static class Wo extends AbstractWo {

	}

}