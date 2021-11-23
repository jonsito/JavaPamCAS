<?php
require_once(__DIR__."/logging.php");
require_once("/var/www/.ssh/ldap_config.php");

class AuthLDAP {
    var $users;
    var $myLogger;

    function __construct () {
        $this->users=array();
        $this->myLogger=new Logger("AuthLDAP",LEVEL_TRACE);
    }

    function getUserData($user) {
        // initialize user data
        $this->users[$user]=array();
        $this->users[$user]['userid']=-1;
        $this->users[$user]['groupid']=-1;

        // connect to ldap server
        $conn= ldap_connect(LDAP_SERVER,LDAP_PORT);
        if (!$conn) return false;
        if (! ldap_set_option($conn,LDAP_OPT_PROTOCOL_VERSION,LDAP_VERSION) ) {
            ldap_close($conn);
            return null;
        }
        $r=ldap_bind($conn,LDAP_QUERYDN,LDAP_QUERYPW);
        if (!$r) { // bind error
            ldap_close($conn);
            return null;
        }

        // query user data to ldap server
        $filter="(uid=".$user.")";
        $query=array('uid','uidNumber','gidNumber','gecos','irisPersonalUniqueID');
        $result=ldap_search($conn,LDAP_AUTHDN,$filter,$query);
        if(!$result) { // error en consulta
            ldap_close($conn);
            return null;
        }
        // retrieve userid and gid
        $entry=ldap_first_entry($conn,$result);
        if (!$entry) { // no entries
            ldap_close($conn);
            return null;
        }
        // buscamos los datos
        $attrs=ldap_get_attributes($conn,$entry);
        // rellenamos la tabla de resultados
        $this->users[$user]['userid']=$attrs["uidNumber"][0];
        $this->users[$user]['groupid']=$attrs["gidNumber"][0];
        // los usuarios creados "a mano" pertenecen siempre a la escuela :-)
        $this->users[$user]['displayName']=$attrs["gecos"][0];
        $this->users[$user]['upmCentre']="09";
        $this->users[$user]['uid']=$attrs["uid"][0];
        $this->users[$user]['upmPersonalUniqueID']=$attrs["irisPersonalUniqueID"][0];
        ldap_close($conn);
        // echo json_encode($this->users[$user]);
        return $this->users[$user];
    }

    function login($user,$password="") {
        if (defined(DEBUG_USER)) {
            if ($user===DEBUG_USER) return true;
        }
        $conn= ldap_connect(LDAP_SERVER,LDAP_PORT);
        if (!$conn) {
            $this->myLogger->error("LDAP Connect failed for user:{$user} host:{$_SERVER['REMOTE_ADDR']}");
            return false;
        }
        if (! ldap_set_option($conn,LDAP_OPT_PROTOCOL_VERSION,LDAP_VERSION) ) {
            ldap_close($conn);
            return false;
        }
        if(!ctype_graph($user)) return false; // no valid login
        // Intentamos hacer bind con el user y el pass dados
        $dn="uid=".$user.",".LDAP_AUTHDN;
        $res= @ldap_bind($conn,$dn,$password);
        ldap_close($conn);
        if (!$res) {
            $this->myLogger->error("AUTH: Authentication failed for user:{$user} host:{$_SERVER['REMOTE_ADDR']}");
            return false;
        }
        $this->myLogger->info("AUTH: Authentication success for user:{$user} host:{$_SERVER['REMOTE_ADDR']}");
        return true;
    }
}

/**
 * get a variable from _REQUEST array
 * @param {string} $name variable name
 * @param {string} $type default type (i,s,b)
 * @param {string} $def default value. may be null
 * @param {boolean} $esc true if variable should be MySQL escape'd to avoid SQL injection
 * @return {object} requested value (int,string,bool) or null if invalid type
 */
function http_request($name,$type,$def) {
    $a=$def;
    if (isset($_REQUEST[$name])) $a=$_REQUEST[$name];
    if ($a===null) return null;
    switch ($type) {
        case "s": if ($a===_('-- Search --') ) $a=""; // filter "search" in searchbox  ( should already be done in js side)
            return strval($a);
        case "i": return intval($a);
        case "b":
            if ($a==="") return $def;
            return toBoolean($a);
        case "d":
        case "f": return floatval(str_replace("," ,"." ,$a));
    }
    do_log("request() invalid type:$type requested");
    return null;
}

$user=http_request("username","s","");
$pass=http_request("password","s","");
$auth= new AuthLDAP();
$res = $auth->login($user,$pass);
if ($res) {
    // retrieve and print data from request
    $data=$auth->getUserData($user);
    echo "<html><head></head><body><table>".PHP_EOL;
    foreach ($data as $key => $val) {
        echo "<tr>".PHP_EOL;
        echo "<td><kbd><span>".$key."</span></kbd></td>".PHP_EOL;
        echo "<td><code><span>[".$val."]</span></code></td>".PHP_EOL;
        echo "</tr>".PHP_EOL;
    }
    echo "</table></body></html>".PHP_EOL;
} else {
    // mark error and force reload main page
    echo "<html><head></head><body>".PHP_EOL;
    echo "Credenciales invalidas".PHP_EOL;
    echo "</body></html>".PHP_EOL;
}