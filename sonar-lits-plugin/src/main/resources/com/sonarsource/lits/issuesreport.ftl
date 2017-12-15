<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <title>LITS Issues report</title>
  <link href="files/sonar.css" media="all" rel="stylesheet" type="text/css">
  <link rel="shortcut icon" type="image/x-icon" href="files/favicon.ico">
  <script type="text/javascript" src="files/jquery.min.js"></script>
</head>
<body>
<div id="reportHeader">
  <div class="title">LITS Different Issues Report</div>
</div>

<div id="content">

  <div id="summary">
  <table width="100%">
    <tbody>
    <tr>
    <#assign size = '33'>
    <td align="center" width="${size}%" class="all">
      <h3>Issues</h3>
      <span class="big">${report.issuesCount()?c}</span>
    </td>
    </tr>
    </tbody>
  </table>
  </div>

  <br/>

  <div id="summary-per-file">
  <#list report.resourceReports() as resourceReport>
      <#assign issueId=0>
      <#assign tableCss = ''>
  <table width="100%" class="data ${tableCss}" id="resource-${resourceReport_index?c}">
    <thead>
    <tr class="total">
      <th align="left" colspan="2" nowrap>
        <div class="file_title">
          <img src="files/${resourceReport.type()}.png" title="${resourceReport.componentKey()}"/>
          <a href="#" onclick="$('.resource-details-${resourceReport_index?c}').toggleClass('masked'); return false;" style="color: black" title="${resourceReport.componentKey()}">${resourceReport.componentKey()}</a>
        </div>
      </th>
      <th align="right" width="1%" nowrap class="resource-details-${resourceReport_index?c} all">
        <span id="current-total">${resourceReport.issues()?size?c}</span><br/>Issues
      </th>
    </tr>
    </thead>
    <tbody class="resource-details-${resourceReport_index?c}">
    <#assign colspan = '5'>
    <#assign issues=resourceReport.issuesAtLine(0)>
      <#if issues?has_content>
      <tr class="globalIssues">
        <td colspan="${colspan}">
          <#list issues as issue>
            <div class="issue" id="${issue.key()}">
              <div class="vtitle">
                <i class="icon-severity-${issue.severity()?lower_case}"></i>
                <span class="rulename">${issue.message()?html}</span>
                &nbsp;
                <img src="files/sep12.png">&nbsp;
                <span class="rule_key">${issue.ruleKey()}</span>
                
              </div>
            </div>
            <#assign issueId = issueId + 1>
          </#list>
        </td>
      </tr>
      </#if>
      <tr>
        <td colspan="${colspan}">
          <table class="sources" border="0" cellpadding="0" cellspacing="0">
            <#list resourceReport.getEscapedSource() as line>
              <#assign lineIndex=line_index+1>
              <#if resourceReport.isDisplayableLine(lineIndex)>
                <tr id="${resourceReport_index?c}L${lineIndex?c}" class="row">
                  <td class="lid ">${lineIndex?c}</td>
                  <td class="line ">
                    <pre>${line}</pre>
                  </td>
                </tr>
                <tr id="${resourceReport_index}S${lineIndex?c}" class="blockSep">
                  <td colspan="2"></td>
                </tr>
                <#assign issues=resourceReport.issuesAtLine(lineIndex)>
                <#if issues?has_content>
                  <tr id="${resourceReport_index?c}LV${lineIndex?c}" class="row">
                    <td class="lid"></td>
                    <td class="issues">
                      <#list issues as issue>
                        <div class="issue" id="${issue.key()}">
                          <div class="vtitle">
                            <i class="icon-severity-${issue.severity()?lower_case}"></i>
                            <span class="rulename">${issue.message()?html}</span>
                            &nbsp;
                            <img src="files/sep12.png">
						                <span class="rule_key">${issue.ruleKey()}</span>
                            &nbsp;

                          </div>
                        </div>
                        <#assign issueId = issueId + 1>
                      </#list>
                    </td>
                  </tr>
                </#if>
              </#if>
            </#list>
          </table>
        </td>
      </tr>
    </tbody>
  </table>
  </#list>
  </div>
</div>
<script type="text/javascript">
    var issuesPerResource = [
    <#list report.resourceReports() as resourceReport>
      [
        <#assign issues=resourceReport.issues()>
        <#list issues as issue>
          {'r': 'R${issue.ruleKey()}', 'l': ${(issue.line()!0)?c}, 's': '${issue.severity()?lower_case}'}<#if issue_has_next>,</#if>
        </#list>
      ]
      <#if resourceReport_has_next>,</#if>
    </#list>
    ];
    var nbResources = ${report.resourceReports()?size?c};
    var separators = new Array();

   /* lineIds must be sorted */
  function showSeparators(fileIndex, lineIds) {
    var lastSeparatorId = 9999999;
    for (var lineIndex = 0; lineIndex < lineIds.length; lineIndex++) {
      var lineId = lineIds[lineIndex];
      if (lineId > 0) {
        if (lineId > lastSeparatorId) {
          var separator = $('#' + fileIndex + 'S' + lastSeparatorId);
          if (separator != null) {
            separator.addClass('visible');
            separators.push(separator);
          }
        }
        lastSeparatorId = lineId + 2;
      }
    }
  }

  $(function() {
    for (var resourceIndex = 0; resourceIndex < nbResources; resourceIndex++) {
      var linesToDisplay = $.map(issuesPerResource[resourceIndex], function(v, i) {
        return v['l'];
      });

      linesToDisplay.sort();
      showSeparators(resourceIndex, linesToDisplay);
    }
  });
</script>
</body>
</html>
