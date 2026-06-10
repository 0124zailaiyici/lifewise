import java.sql.*;
public class CheckDB {
  public static void main(String[] args) throws Exception {
    Connection c = DriverManager.getConnection("jdbc:h2:D:\\demo\\AI\\codex\\LifeWise\\lifewise;IFEXISTS=TRUE", "sa", "");
    Statement s = c.createStatement();
    ResultSet rs = s.executeQuery("SELECT ID, USERNAME, PHONE FROM USERS");
    while (rs.next()) {
      System.out.println("ID=" + rs.getInt("ID") + " username=" + rs.getString("USERNAME") + " phone=" + rs.getString("PHONE"));
    }
    rs.close(); s.close(); c.close();
  }
}
