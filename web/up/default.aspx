<%@ Page Language="C#" %>
<script runat="server" language="C#">
public void Page_Load(object sender, EventArgs e)
{
string lasttimestamp = Request.Params["lasttimestamp"];
System.Net.HttpWebRequest request = (System.Net.HttpWebRequest)System.Net.WebRequest.Create("http://localhost:8123/" + lasttimestamp);
System.Net.WebResponse response = request.GetResponse();
System.IO.Stream responseStream = response.GetResponseStream();
System.IO.StreamReader reader = new System.IO.StreamReader(responseStream);
Response.ContentType = "text/plain";
Response.Write(reader.ReadToEnd());
reader.Close();
responseStream.Close();
response.Close();
}
</script>