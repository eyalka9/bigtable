export const generateSampleData = (rowCount = 300000, columnCount = 40) => {
  // Define column schema
  const schema = [];
  const columnNames = [];
  
  for (let i = 0; i < columnCount; i++) {
    const columnName = `column_${i + 1}`;
    columnNames.push(columnName);
    
    // Vary data types for testing
    let dataType = 'STRING';
    if (i % 5 === 0) dataType = 'INTEGER';
    else if (i % 7 === 0) dataType = 'DOUBLE';
    else if (i % 11 === 0) dataType = 'BOOLEAN';
    else if (i % 13 === 0) dataType = 'DATE';
    
    schema.push({
      name: columnName,
      type: dataType,
      sortable: true,
      filterable: true,
      searchable: dataType === 'STRING'
    });
  }
  
  // Generate sample data
  const data = [];
  const sampleStrings = [
    'Lorem', 'ipsum', 'dolor', 'sit', 'amet', 'consectetur', 'adipiscing', 'elit',
    'sed', 'do', 'eiusmod', 'tempor', 'incididunt', 'ut', 'labore', 'et', 'dolore',
    'magna', 'aliqua', 'Ut', 'enim', 'ad', 'minim', 'veniam', 'quis', 'nostrud'
  ];
  
  for (let row = 0; row < rowCount; row++) {
    const rowData = {};
    
    for (let col = 0; col < columnCount; col++) {
      const columnName = columnNames[col];
      const schemaItem = schema[col];
      
      switch (schemaItem.type) {
        case 'INTEGER':
          rowData[columnName] = Math.floor(Math.random() * 10000);
          break;
        case 'DOUBLE':
          rowData[columnName] = (Math.random() * 1000).toFixed(2);
          break;
        case 'BOOLEAN':
          rowData[columnName] = Math.random() > 0.5;
          break;
        case 'DATE':
          const date = new Date(2020 + Math.floor(Math.random() * 4), 
                               Math.floor(Math.random() * 12), 
                               Math.floor(Math.random() * 28) + 1);
          rowData[columnName] = date.toISOString().split('T')[0];
          break;
        default: // STRING
          const wordCount = Math.floor(Math.random() * 3) + 1;
          const words = [];
          for (let w = 0; w < wordCount; w++) {
            words.push(sampleStrings[Math.floor(Math.random() * sampleStrings.length)]);
          }
          rowData[columnName] = words.join(' ');
          break;
      }
    }
    
    data.push(rowData);
  }
  
  return { data, schema };
};