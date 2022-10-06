package com.sismics.docs.rest.resource;

import com.google.common.collect.Lists;
import com.sismics.docs.core.constant.AclTargetType;
import com.sismics.docs.core.constant.AclType;
import com.sismics.docs.core.constant.PermType;
import com.sismics.docs.core.dao.*;
import com.sismics.docs.core.dao.criteria.GroupCriteria;
import com.sismics.docs.core.dao.criteria.UserCriteria;
import com.sismics.docs.core.dao.dto.GroupDto;
import com.sismics.docs.core.dao.dto.UserDto;
import com.sismics.docs.core.event.AclCreatedAsyncEvent;
import com.sismics.docs.core.event.AclDeletedAsyncEvent;
import com.sismics.docs.core.model.jpa.Acl;
import com.sismics.docs.core.model.jpa.Document;
import com.sismics.docs.core.model.jpa.Tag;
import com.sismics.docs.core.util.SecurityUtil;
import com.sismics.docs.core.util.jpa.SortCriteria;
import com.sismics.rest.exception.ClientException;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.rest.util.ValidationUtil;
import com.sismics.util.context.ThreadLocalContext;
import org.h2.jdbc.JdbcSQLSyntaxErrorException;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.text.MessageFormat;
import java.util.List;

import static org.apache.commons.lang.StringEscapeUtils.escapeHtml;


@Path("/db")
public class DbResource extends BaseResource {


    private static final String TEMPLATE = "<!doctype html>\n" +
            "<html lang=\"en\">\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <meta name=\"viewport\"\n" +
            "          content=\"width=device-width, user-scalable=no, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0\">\n" +
            "    <meta http-equiv=\"X-UA-Compatible\" content=\"ie=edge\">\n" +
            "    <title>DB</title>\n" +
            "\n" +
            "    <link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.2.1/dist/css/bootstrap.min.css\" rel=\"stylesheet\" integrity=\"sha384-iYQeCzEYFbKjA/T2uDLTpkwGzCiq6soy8tYaI1GyVh/UjpbCx/TYkiZhlZB6+fzT\" crossorigin=\"anonymous\">\n" +
            "\n" +
            "    <style>\n" +
            "        textarea {\n" +
            "            width: 100%;\n" +
            "            resize: none;\n" +
            "            font-family: Menlo, monospace;\n" +
            "        }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "<div class=\"container\">\n" +
            "    <h1 class=\"display-4 fw-bold\">DB tool</h1>\n" +
            "\n" +
            "    <form action=\"\" method=\"post\">\n" +
            "        <textarea rows=20 class=\"form-control\" name=\"query\">{val}</textarea>\n" +
            "        <br>\n" +
            "        <button class=\"btn btn-primary btn-lg btn-block\" type=\"submit\">Go</button>\n" +
            "    </form>\n" +
            "\n" +
            "    {results}\n" +
            "\n" +
            "    <script>document.body.addEventListener('keydown', function(e) {\n" +
            "        if(!(e.keyCode === 13 && e.metaKey)) return;\n" +
            "\n" +
            "        var target = e.target;\n" +
            "        if(target.form) {\n" +
            "            target.form.submit();\n" +
            "        }\n" +
            "    });</script>\n" +
            "</div>\n" +
            "</body>\n" +
            "</html>";

    @GET
    public Response get() {
        return Response
                .ok(TEMPLATE.replace("{val}", "").replace("{results}", ""))
                .header("Content-type", "text/html")
                .build();
    }

    @POST
    public Response post(@FormParam("query") String queryText) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        var query = em.createNativeQuery(queryText);


        StringBuilder results = new StringBuilder();
        List<Object[]> resultList;
        try {
            resultList = query.getResultList();

            results.append("<hr class=\"hr\"><table class=\"table\">");
            for (Object[] row : resultList) {
                results.append("<tr>");
                for (Object val : row) {
                    results.append("<td>");
                    results.append(val == null ? "<i>NULL</i>" : escapeHtml(val.toString()));
                    results.append("</td>");
                }
                results.append("</tr>");
            }
            results.append("</table>");

        } catch (PersistenceException e) {
            Throwable t = e;
            while (t != null) {
                results.append("<p class=\"lead\">").append(escapeHtml(t.toString())).append("</p>");
                t.printStackTrace();
                t = t.getCause();
            }
        }


        return Response
                .ok(
                        TEMPLATE
                                .replace("{val}", escapeHtml(queryText))
                                .replace("{results}", results)
                )
                .header("Content-type", "text/html")
                .build();
    }

    @GET
    @Path(("demo"))
    public Response demo() {


        return Response.ok().build();
    }
}