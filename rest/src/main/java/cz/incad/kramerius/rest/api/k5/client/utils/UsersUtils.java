/*
 * Copyright (C) 2013 Pavel Stastny
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package cz.incad.kramerius.rest.api.k5.client.utils;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import cz.incad.kramerius.rest.apiNew.admin.v10.rights.RolesResource;
import cz.incad.kramerius.security.Role;
import cz.incad.kramerius.security.User;

public class UsersUtils {

    public static final String LNAME = "lname";
    public static final String FIRSTNAME = "firstname";
    public static final String SURNAME = "surname";
    public static final String ID = "id";
    public static final String ROLES = "roles";
    public static final String LICENSES = "licenses";
    public static final String SESSION = "session";

    public static JSONObject userToJSON(User user) throws JSONException {
        return  userToJSON(user, new ArrayList<>());
    }

    public static JSONObject userToJSON(User user, List<String> labels) throws JSONException {
        JSONObject jsonObj = new JSONObject();
        jsonObj.put(LNAME, user.getLoginname());
        jsonObj.put(FIRSTNAME, user.getFirstName());
        jsonObj.put(SURNAME, user.getSurname());
        jsonObj.put(ID, user.getId());

        JSONArray jsonArr = new JSONArray();
        Role[] roles = user.getGroups();
        if (roles != null) {
            for (Role r : roles) {
                JSONObject json = RolesResource.roleToJSON(r);
                jsonArr.put(json);
            }
            jsonObj.put(ROLES, jsonArr);
        }

        JSONArray labelsArray = new JSONArray();
        labels.stream().forEach(labelsArray::put);
        jsonObj.put(LICENSES, labelsArray);


        JSONObject jsonSessionAttributes = new JSONObject();
        user.getSessionAttributes().keySet().stream().forEach(key-> jsonSessionAttributes.put(key, user.getSessionAttributes().get(key)));
        jsonObj.put(SESSION, jsonSessionAttributes);



        return jsonObj;
    }


}
