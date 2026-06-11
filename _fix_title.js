var fs = require('fs');
var c = fs.readFileSync('frontend/src/views/Chat.vue', 'utf8');

// Fix 1: For fashion scenes, dont use occasion as title (to avoid duplicate)
var oldTitleLogic = '  if (!title) title = data.\u54c1\u7c7b || data.problem || data.question || data.occasion || ""';
var newTitleLogic = '  // For fashion, dont use occasion as title (shown as tag)\n  if (!title) title = data.\u54c1\u7c7b || data.problem || data.question || ""\n  if (!title && scene !== "fashion") title = data.occasion || ""';
if (c.includes(oldTitleLogic)) {
  c = c.replace(oldTitleLogic, newTitleLogic);
  fs.writeFileSync('frontend/src/views/Chat.vue', c, 'utf8');
  console.log('Title logic updated');
} else {
  console.log('ERROR: old title logic not found');
}