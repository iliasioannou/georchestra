/*
 * Copyright (C) 2009-2018 by the geOrchestra PSC
 *
 * This file is part of geOrchestra.
 *
 * geOrchestra is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * geOrchestra is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.georchestra.console.ws.edituserdetails;

import org.georchestra.console.ds.AccountDao;
import org.georchestra.console.ds.DataServiceException;
import org.georchestra.console.ds.DuplicatedEmailException;
import org.georchestra.console.ds.OrgsDao;
import org.georchestra.console.dto.Account;
import org.georchestra.console.dto.Org;
import org.georchestra.console.ws.utils.Validation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Support for the Edit Account user interactions.
 *
 * @author Mauricio Pazos
 *
 */
@Controller
@SessionAttributes(types=EditUserDetailsFormBean.class)
public class EditUserDetailsFormController {

	private OrgsDao orgsDao;
	private AccountDao accountDao;

	private Validation validation;

	@Autowired
	public EditUserDetailsFormController(AccountDao dao, OrgsDao orgsDao, Validation validation){
		this.accountDao = dao;
		this.orgsDao = orgsDao;
		this.validation = validation;
	}

	private static final String[] fields = {"uid", "firstName", "surname", "email", "title", "phone", "facsimile", "org", "description", "postalAddress"};

	@InitBinder
	public void initForm( WebDataBinder dataBinder) {

		dataBinder.setAllowedFields(fields);
	}


	/**
	 * Retrieves the account data and sets the model before presenting the edit form view.
	 *
	 * @param model
	 *
	 * @return the edit form view
	 *
	 * @throws IOException
	 */
	@RequestMapping(value="/account/userdetails", method=RequestMethod.GET)
	public String setupForm(HttpServletRequest request, HttpServletResponse response,  Model model) throws IOException{

		if(request.getHeader("sec-username") == null) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return null;
		}

		try {

			HttpSession session = request.getSession();
			EditUserDetailsFormBean formBean = createForm(this.accountDao.findByUID(request.getHeader("sec-username")));

			model.addAttribute(formBean);
			for (String f : fields) {
				if (this.validation.isUserFieldRequired(f)) {
					session.setAttribute(f + "Required", "true");
				}
			}

			return "editUserDetailsForm";

		} catch (Exception e) {
			e.printStackTrace();
			throw new IOException(e);
		}
	}


	/**
	 * Creates a form based on the account data.
	 *
	 * @param account input data
	 */
	private EditUserDetailsFormBean createForm(final Account account) {

		EditUserDetailsFormBean formBean = new EditUserDetailsFormBean();

		formBean.setUid(account.getUid());
		formBean.setEmail(account.getEmail());
		formBean.setFirstName(account.getGivenName());
		formBean.setSurname(account.getSurname());
		formBean.setTitle(account.getTitle());
		formBean.setPhone(account.getPhone());
		formBean.setFacsimile(account.getFacsimile());
		formBean.setDescription(account.getDescription());
		formBean.setPostalAddress(account.getPostalAddress());
		if(account.getOrg().length() > 0) {
			Org org = this.orgsDao.findByCommonName(account.getOrg());
			formBean.setOrg(org.getName());
		} else {
			formBean.setOrg("");
		}

		return formBean;
	}

	/**
	 * Generates a new password, then an e-mail is sent to the user to inform that a new password is available.
	 *
	 * @param formBean		Contains the user's email
	 * @param resultErrors 	will be updated with the list of found errors.
	 * @param sessionStatus
	 *
	 * @return the next view
	 *
	 * @throws IOException
	 */
	@RequestMapping(value="/account/userdetails", method=RequestMethod.POST)
	public String edit(
						HttpServletRequest request,
						HttpServletResponse response,
						Model model,
						@ModelAttribute EditUserDetailsFormBean formBean,
						BindingResult resultErrors,
						SessionStatus sessionStatus)
						throws IOException {
		String uid = formBean.getUid();
		try {
			if(!request.getHeader("sec-username").equals(uid))
				response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (NullPointerException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		}

		// Validate first name and surname
		if(!StringUtils.hasLength(formBean.getFirstName()) && this.validation.isUserFieldRequired("firstName"))
			resultErrors.rejectValue("firstName", "firstName.error.required", "required");

		if(!StringUtils.hasLength( formBean.getSurname() ) && this.validation.isUserFieldRequired("surname"))
			resultErrors.rejectValue("surname", "surname.error.required", "required");

		this.validation.validateUserField("phone", formBean.getPhone(), resultErrors);
		this.validation.validateUserField("facsimile", formBean.getFacsimile(), resultErrors);
		this.validation.validateUserField("title", formBean.getTitle(), resultErrors);
		this.validation.validateUserField("description", formBean.getDescription(), resultErrors);
		this.validation.validateUserField("postalAddress", formBean.getPostalAddress(), resultErrors);

		if(resultErrors.hasErrors())
			return "editUserDetailsForm";

		// updates the account details
		try {

			Account account = modify(this.accountDao.findByUID(request.getHeader("sec-username")), formBean);
			this.accountDao.update(account, request.getHeader("sec-username"));

			model.addAttribute("success", true);

			return "editUserDetailsForm";

		} catch (DuplicatedEmailException e) {

			// right now the email cannot be edited (review requirement)
			//resultErrors.addError(new ObjectError("email", "Exist a user with this e-mail"));
			return "createAccountForm";

		} catch (DataServiceException e) {

			throw new IOException(e);
		}


	}

	/**
	 * Modifies the account using the values present in the formBean parameter
	 *
	 * @param account
	 * @param formBean
	 *
	 * @return modified account
	 */
	private Account modify(
			Account account,
			EditUserDetailsFormBean formBean) {

		account.setGivenName( formBean.getFirstName() );
		account.setSurname(formBean.getSurname());
		account.setTitle( formBean.getTitle() );
		account.setPhone(formBean.getPhone());
		account.setFacsimile(formBean.getFacsimile());
		account.setDescription(formBean.getDescription());
		account.setPostalAddress(formBean.getPostalAddress());

		return account;
	}
	
	@ModelAttribute("editUserDetailsFormBean")
	public EditUserDetailsFormBean getEditUserDetailsFormBean() {
		return new EditUserDetailsFormBean();
	}
}
