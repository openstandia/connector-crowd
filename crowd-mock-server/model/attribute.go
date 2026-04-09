package model

import "encoding/xml"

// <attributes>
//   <attribute name="x">
//     <values><value>v1</value><value>v2</value></values>
//   </attribute>
// </attributes>
type AttributeList struct {
	XMLName    xml.Name          `xml:"attributes"`
	Attributes []AttributeEntity `xml:"attribute"`
}

type AttributeEntity struct {
	XMLName xml.Name        `xml:"attribute"`
	Name    string          `xml:"name,attr"`
	Values  AttributeValues `xml:"values"`
}

type AttributeValues struct {
	Value []string `xml:"value"`
}

func NewEmptyAttributeList() *AttributeList {
	return &AttributeList{
		Attributes: []AttributeEntity{},
	}
}
