package com.tabgrinder.brucetrigger.audit;
final class WaitingGameFixtures {static String json()throws Exception{String encoded="H4sIAAAAAAACA61TwW6DMAz9lYrzDo7j2Ol+ZZoqRgn0UlA7aZqm/fseMEoZ9IIWCTm28PPzs/OV1aeq/sg/D5e8KC/Z8+4ly3OK6skJEeHusqddFwvUnzXP" +
"vbnOQYwjrEu/R6Lu1TSaqbM9U2B16jUYj5kcxkxv95mBjPGfcpISNpow+SOTeI3AKIEa4BX9PdzQaEQTnfEQTSpDjgkQemsdm+7s1bNb9hLCjBF4a4HP37gc" +
"4ZU2VfdjpspKL948FJihgEvqYrDO2JCVvQKqPZ2rtjlXG6eBtkZN+U8HHkwS6hc8KTz00nFScEqY0TAzWSoibqsiPcHtilxbbOfhWjfN+78vKYrRsJY9mQiB" +
"EsrfL6k+WNKNgrJbX9OloBMfPITH4obt4n7/AKQ+et8CBAAA";
java.util.zip.GZIPInputStream in=new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(android.util.Base64.decode(encoded,0)));java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);in.close();return new String(out.toByteArray(),"UTF-8");}}
