package models.menu.items;

import models.menu.Menu;
import models.menu.MenuItem;
import play.mvc.Router;
import java.util.HashMap;
import java.util.Map;
import jregex.Matcher;
import jregex.Pattern;
import play.Logger;
import val.ControllerChain;

/**
 *
 * @author keruspe
 */
public class MenuItem_ControllerChain extends MenuItem {

    @ControllerChain
    public String controllerChain;

    public MenuItem_ControllerChain(String controllerChain, String displayStr, Menu menu) {
        //super(displayStr, menu);
        this.controllerChain = controllerChain.trim(); // Morphia failure with latest play
        this.displayStr = displayStr;
        this.menu = menu;
    }

    public MenuItem_ControllerChain(String controllerChain, String displayStr) {
        //super(displayStr);
        this.displayStr = displayStr;
        this.controllerChain = controllerChain.trim();
    }

    public void setControllerChain(String controllerChain) {
        this.controllerChain = controllerChain.trim();
    }

    @Override
    public String getLink() {
        if (!controllerChain.endsWith(")")) {
            controllerChain = controllerChain + "()";
        }
        Matcher m = new Pattern("^({action}[^\\s(]+)({params}.+)?(\\s*)$").matcher(controllerChain);
        if (m.matches()) {
            String params = m.group("params");
            Map<String, Object> staticArgs = new HashMap<String, Object>();

            if (params == null || params.length() < 1) {
                return Router.reverse(m.group("action")).url;
            }
            params = params.substring(1, params.length() - 1);
            for (String param : params.split(",")) {
                Matcher matcher = new Pattern("([a-zA-Z_0-9]+):'(.*)'").matcher(param);
                if (matcher.matches()) {
                    staticArgs.put(matcher.group(1), matcher.group(2));
                } else {
                    Logger.warn("Ignoring %s (static params must be specified as key:'value',...)", params);
                }
            }

            return Router.reverse(m.group("action"), staticArgs).url;
        }
        return Router.reverse(this.controllerChain).url;
    }

   @Override
   public String getValue() {
      return controllerChain;
   }

   @Override
   public String getType() {
      return "ControllerChain";
   }

   @Override
   public void setValue(String value) {
      if (value != null) {
         this.controllerChain = value;
      }
   }
}
